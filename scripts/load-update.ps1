# Temporary MINIBRAIN_UPDATE loader: validates the file, then creates data through the existing REST API.
# ponytail: no preview, no Revision, not atomic (validates everything first to avoid half-loads).
# Replaced by the real import (roadmap steps 10-13).
#   powershell -ExecutionPolicy Bypass -File scripts/load-update.ps1 -File path\to\update.json
# Supports: newSkills, changes[].evidenceAdded, changes[].openQuestionsAdded, newRelations.
# Rejects (not implemented yet): changes[].proposedStatus, changes[].openQuestionsResolved.
param(
    [Parameter(Mandatory)][string]$File,
    [string]$Api = 'http://localhost:8080/api'
)

$ErrorActionPreference = 'Stop'
$update = Get-Content -Raw -Encoding UTF8 $File | ConvertFrom-Json
$newSkills = @($update.newSkills)
$changes = @($update.changes)
$relations = @($update.newRelations)

# --- validate everything before writing anything ---
$errors = New-Object System.Collections.Generic.List[string]
if ($update.type -ne 'MINIBRAIN_UPDATE' -or $update.schemaVersion -ne 1) {
    $errors.Add('expected type MINIBRAIN_UPDATE with schemaVersion 1')
}
$statuses = 'DISCOVERED', 'LEARNING', 'UNDERSTOOD', 'APPLIED', 'MASTERED'
$relationTypes = 'PART_OF', 'REQUIRES', 'RELATED_TO', 'LEADS_TO'
$existing = @((Invoke-RestMethod "$Api/graph").nodes | ForEach-Object { $_.key })
$newKeys = @($newSkills | ForEach-Object { $_.key })
$known = $existing + $newKeys

foreach ($s in $newSkills) {
    if ($s.key -notmatch '^[a-z0-9]+(-[a-z0-9]+)*(\.[a-z0-9]+(-[a-z0-9]+)*)*$') { $errors.Add("bad key format: $($s.key)") }
    if (-not $s.name) { $errors.Add("skill $($s.key): name is required") }
    if ($statuses -notcontains $s.status) { $errors.Add("skill $($s.key): unknown status $($s.status)") }
    if ($existing -contains $s.key) { $errors.Add("skill $($s.key) already exists") }
}
$newKeys | Group-Object | Where-Object Count -gt 1 | ForEach-Object { $errors.Add("duplicate key in file: $($_.Name)") }
foreach ($c in $changes) {
    if ($known -notcontains $c.skill) { $errors.Add("changes: unknown skill $($c.skill)") }
    if ($c.proposedStatus) { $errors.Add("changes $($c.skill): proposedStatus is not supported by this loader yet") }
    # Where-Object drops $null: in PowerShell 5.1 @($null).Count is 1, so a missing field would look non-empty.
    if (@($c.openQuestionsResolved | Where-Object { $_ }).Count -gt 0) { $errors.Add("changes $($c.skill): openQuestionsResolved is not supported by this loader yet") }
}
foreach ($r in $relations) {
    if ($known -notcontains $r.from) { $errors.Add("relation: unknown from $($r.from)") }
    if ($known -notcontains $r.to) { $errors.Add("relation: unknown to $($r.to)") }
    if ($relationTypes -notcontains $r.type) { $errors.Add("relation $($r.from) -> $($r.to): unknown type $($r.type)") }
    if ($r.from -eq $r.to) { $errors.Add("relation: $($r.from) relates to itself") }
}
if ($errors.Count -gt 0) {
    Write-Output "Nothing loaded, $($errors.Count) problem(s):"
    $errors | ForEach-Object { Write-Output "  - $_" }
    exit 1
}

# --- write ---
function Post([string]$path, $body) {
    # Explicit UTF-8 bytes: Windows PowerShell 5.1 would otherwise send non-ASCII text (e.g. Russian) garbled.
    $bytes = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress))
    Invoke-RestMethod -Method Post -Uri "$Api$path" -ContentType 'application/json; charset=utf-8' -Body $bytes | Out-Null
}

$evidenceCount = 0; $questionCount = 0
foreach ($s in $newSkills) {
    Post '/skills' @{ key = $s.key; name = $s.name; description = $s.description; status = $s.status }
}
foreach ($c in $changes) {
    foreach ($text in @($c.evidenceAdded)) { if ($text) { Post "/skills/$($c.skill)/evidence" @{ text = $text }; $evidenceCount++ } }
    foreach ($text in @($c.openQuestionsAdded)) { if ($text) { Post "/skills/$($c.skill)/open-questions" @{ text = $text }; $questionCount++ } }
}
foreach ($r in $relations) {
    Post "/skills/$($r.from)/relations" @{ type = $r.type; to = $r.to }
}
Write-Output "Loaded: $($newSkills.Count) skills, $evidenceCount evidence, $questionCount open questions, $($relations.Count) relations"
