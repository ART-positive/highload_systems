param([string]$BaseUrl = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)

function Send-Json {
    param([string]$Method, [string]$Path, [hashtable]$Body)
    $parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        ContentType = 'application/json; charset=utf-8'
    }
    if ($null -ne $Body) {
        $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 5))
    }
    Invoke-RestMethod @parameters
}

$professor = Send-Json POST '/api/v1/users' @{
    fullName = 'Demo Professor'; email = "professor-$suffix@example.org"; role = 'PROFESSOR'; active = $true
}
$student = Send-Json POST '/api/v1/users' @{
    fullName = 'Demo Student'; email = "student-$suffix@example.org"; role = 'STUDENT'; active = $true
}
$program = Send-Json POST '/api/v1/programs' @{
    code = "IVT-$($suffix.ToUpperInvariant())"; name = 'Computer Science'; description = 'Demo program'; archived = $false
}
$course = Send-Json POST '/api/v1/courses' @{
    title = 'Java Backend'; description = 'Spring Boot elective'; professorId = $professor.id; capacity = 1
    startDate = [DateTime]::UtcNow.AddDays(7).ToString('yyyy-MM-dd')
    endDate = [DateTime]::UtcNow.AddDays(60).ToString('yyyy-MM-dd')
}
Send-Json PUT "/api/v1/courses/$($course.id)/programs/$($program.id)" $null | Out-Null
Send-Json PATCH "/api/v1/courses/$($course.id)/status" @{status = 'ENROLLMENT_OPEN'} | Out-Null
$enrollment = Send-Json POST '/api/v1/enrollments' @{studentId = $student.id; courseId = $course.id}
if ($enrollment.status -ne 'ENROLLED') { throw 'Enrollment failed' }
Send-Json PATCH "/api/v1/courses/$($course.id)/status" @{status = 'CANCELLED'} | Out-Null
$cancelled = Send-Json GET "/api/v1/enrollments/$($enrollment.id)" $null
if ($cancelled.status -ne 'CANCELLED') { throw 'Cancellation did not update enrollment' }

[pscustomobject]@{
    ProfessorId = $professor.id
    StudentId = $student.id
    ProgramId = $program.id
    CourseId = $course.id
    EnrollmentId = $enrollment.id
    Result = 'OK: course and enrollment cancelled atomically'
}
