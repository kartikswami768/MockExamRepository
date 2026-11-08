package com.example.mockexam.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mockexam.data.*
import com.example.mockexam.importer.ImportValidationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Navy = Color(0xFF17324D)
private val Teal = Color(0xFF147D78)
private val Page = Color(0xFFF5F7FA)
private val Muted = Color(0xFF64748B)
private val Green = Color(0xFF16803C)
private val Red = Color(0xFFB42318)
private val Amber = Color(0xFF9A6700)

@Composable
fun MockExamTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Navy,
        secondary = Teal,
        background = Page,
        surface = Color.White,
        error = Red
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

@Composable
fun MockExamApp(repository: ExamRepository) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "library") {
        composable("library") { LibraryScreen(repository, nav) }
        composable("history") { HistoryScreen(repository, nav) }
        composable("review") { ReviewScreen(repository, nav) }
        composable("configure/{setId}", arguments = listOf(navArgument("setId") { type = NavType.StringType })) {
            ConfigScreen(repository, nav, it.arguments?.getString("setId").orEmpty())
        }
        composable("test/{attemptId}", arguments = listOf(navArgument("attemptId") { type = NavType.StringType })) {
            TestScreen(repository, nav, it.arguments?.getString("attemptId").orEmpty())
        }
        composable("result/{attemptId}", arguments = listOf(navArgument("attemptId") { type = NavType.StringType })) {
            ResultScreen(repository, nav, it.arguments?.getString("attemptId").orEmpty())
        }
    }
}

@Composable
private fun LibraryScreen(repository: ExamRepository, nav: androidx.navigation.NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sets by remember { mutableStateOf<List<QuestionSetEntity>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var errorDialog by remember { mutableStateOf<List<String>?>(null) }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) { sets = withContext(Dispatchers.IO) { repository.getSets() } }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                importing = true
                status = "Validating workbook…"
                try {
                    val result = withContext(Dispatchers.IO) { repository.importWorkbook(uri) }
                    status = "Imported ${result.questions.size} questions from ${result.sourceFileName}."
                    refresh++
                } catch (e: ImportValidationException) {
                    errorDialog = e.issues
                    status = "Import was not completed."
                } catch (e: Exception) {
                    errorDialog = listOf(e.message ?: "The workbook could not be imported.")
                    status = "Import was not completed."
                } finally {
                    importing = false
                }
            }
        }
    }

    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )) { uri -> if (uri != null) copyAsset(context, uri, "question_template.xlsx", scope) { status = it } }

    val sampleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )) { uri -> if (uri != null) copyAsset(context, uri, "sample_questions.xlsx", scope) { status = it } }

    Scaffold(
        containerColor = Page,
        topBar = { TopAppBarSimple("Mock Exam", "Offline MCQ practice") }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream", "*/*")) }, enabled = !importing, modifier = Modifier.weight(1f)) {
                    Text(if (importing) "Importing…" else "Import Excel")
                }
                OutlinedButton(onClick = { templateLauncher.launch("MockExam_Question_Template.xlsx") }, modifier = Modifier.weight(1f)) {
                    Text("Get template")
                }
            }
            OutlinedButton(onClick = { sampleLauncher.launch("MockExam_Sample_Questions.xlsx") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export sample workbook")
            }
            if (status != null) Text(status!!, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.navigate("history") }, modifier = Modifier.weight(1f)) { Text("Test history") }
                OutlinedButton(onClick = { nav.navigate("review") }, modifier = Modifier.weight(1f)) { Text("Question review") }
            }
            Text("Question banks", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Navy, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            if (sets.isEmpty()) {
                EmptyState(
                    title = "No question banks yet",
                    detail = "Import an .xlsx question bank to create your first mock test. The app keeps imported questions and test data on this device."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(sets, key = { it.id }) { set ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(set.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Navy)
                                Text("${set.questionCount} questions · Imported ${formatDate(set.importedAtMillis)}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                                Text(set.sourceFileName, color = Muted, fontSize = 12.sp)
                                Button(onClick = { nav.navigate("configure/${set.id}") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                    Text("Configure mock test")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (errorDialog != null) {
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            title = { Text("Workbook validation failed") },
            text = { LazyColumn(Modifier.heightIn(max = 360.dp)) { items(errorDialog!!) { Text("• $it", fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) } } },
            confirmButton = { TextButton(onClick = { errorDialog = null }) { Text("Close") } }
        )
    }
}

private fun copyAsset(context: Context, uri: Uri, assetName: String, scope: kotlinx.coroutines.CoroutineScope, onResult: (String) -> Unit) {
    scope.launch {
        val message = withContext(Dispatchers.IO) {
            try {
                val output = context.contentResolver.openOutputStream(uri) ?: error("Could not open destination document.")
                output.use { out -> context.assets.open(assetName).use { it.copyTo(out) } }
                "Saved $assetName."
            } catch (e: Exception) { "Could not save file: ${e.message}" }
        }
        onResult(message)
    }
}

@Composable
private fun ConfigScreen(repository: ExamRepository, nav: androidx.navigation.NavHostController, setId: String) {
    val scope = rememberCoroutineScope()
    var set by remember { mutableStateOf<QuestionSetEntity?>(null) }
    var questions by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var pickSpecific by remember { mutableStateOf(false) }
    var perQuestionSeconds by remember { mutableStateOf("60") }
    var positive by remember { mutableStateOf("4") }
    var negative by remember { mutableStateOf("1") }
    var skipped by remember { mutableStateOf("0") }
    var negativeEnabled by remember { mutableStateOf(true) }
    var testName by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(setId) {
        set = withContext(Dispatchers.IO) { repository.getSet(setId) }
        questions = withContext(Dispatchers.IO) { repository.getQuestions(setId) }
        selectedIds.clear()
        selectedIds.addAll(questions.map { it.questionId })
        testName = set?.name.orEmpty()
    }
    val count = if (pickSpecific) selectedIds.size else questions.size

    Scaffold(containerColor = Page, topBar = { TopAppBarSimple("Configure test", set?.name ?: "Loading question bank") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Question selection") {
                Text("${questions.size} questions available", color = Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !pickSpecific, onClick = { pickSpecific = false }, label = { Text("All questions") })
                    FilterChip(selected = pickSpecific, onClick = { pickSpecific = true }, label = { Text("Pick questions") })
                }
                if (pickSpecific) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedIds.clear(); selectedIds.addAll(questions.map { it.questionId }) }) { Text("Select all") }
                        TextButton(onClick = { selectedIds.clear() }) { Text("Clear") }
                    }
                    Text("Selected: ${selectedIds.size} / ${questions.size}", color = Muted, fontSize = 12.sp)
                    Column(Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState())) {
                        questions.forEach { q ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = q.questionId in selectedIds, onCheckedChange = { checked ->
                                    if (checked) selectedIds.add(q.questionId) else selectedIds.remove(q.questionId)
                                })
                                Text("${q.questionId} · ${q.question}", maxLines = 2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            SectionCard("Exam timing") {
                NumberField("Time per question (seconds)", perQuestionSeconds, { perQuestionSeconds = it })
                val configuredSeconds = perQuestionSeconds.toLongOrNull()
                val calculatedTotal = configuredSeconds?.times(count.toLong())
                Text(
                    if (count > 0 && configuredSeconds != null && configuredSeconds > 0 && calculatedTotal != null) {
                        "$count questions × $configuredSeconds seconds = ${formatDuration(calculatedTotal)} total"
                    } else {
                        "Select at least one question and enter a valid time allowance."
                    },
                    color = Teal,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text("This is one continuous countdown for the entire test. You can spend more or less time on individual questions; moving between questions never resets the timer.", color = Muted, fontSize = 12.sp)
            }
            SectionCard("Scoring") {
                NumberField("Positive marks per correct answer", positive, { positive = it }, decimal = true)
                NumberField("Negative marks per incorrect answer", negative, { negative = it }, decimal = true)
                NumberField("Marks for a skipped question", skipped, { skipped = it }, decimal = true)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable negative marking", modifier = Modifier.weight(1f))
                    Switch(checked = negativeEnabled, onCheckedChange = { negativeEnabled = it })
                }
            }
            SectionCard("Test name") { OutlinedTextField(value = testName, onValueChange = { testName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            if (validation != null) Text(validation!!, color = Red, fontSize = 13.sp)
            Button(
                enabled = !creating && questions.isNotEmpty(),
                onClick = {
                    val p = positive.toDoubleOrNull()
                    val n = negative.toDoubleOrNull()
                    val s = skipped.toDoubleOrNull()
                    val perQuestion = perQuestionSeconds.toIntOrNull()
                    val totalTime = if (perQuestion != null && count > 0) perQuestion.toLong() * count else 0L
                    val time = totalTime.takeIf { it in 1L..86_400L }?.toInt()
                    val problem = when {
                        count <= 0 -> "Select at least one question."
                        p == null || p <= 0.0 -> "Positive marks must be greater than zero."
                        n == null || n < 0.0 -> "Negative marks must be zero or greater."
                        s == null || s < 0.0 -> "Skipped marks must be zero or greater."
                        perQuestion == null || perQuestion <= 0 -> "Enter a valid time per question."
                        time == null -> "Calculated total duration must be between 1 second and 24 hours."
                        else -> null
                    }
                    if (problem != null) validation = problem else {
                        creating = true
                        validation = null
                        scope.launch {
                            try {
                                val chosen = if (pickSpecific) selectedIds.toList() else questions.map { it.questionId }
                                val attemptId = withContext(Dispatchers.IO) {
                                    repository.createAttempt(setId, chosen, testName, time!!, perQuestion!!, p!!, n!!, s!!, negativeEnabled)
                                }
                                nav.navigate("test/$attemptId")
                            } catch (e: Exception) { validation = e.message ?: "Could not start the test." }
                            finally { creating = false }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()
            ) { Text(if (creating) "Preparing…" else "Start mock test") }
            TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        }
    }
}

@Composable
private fun TestScreen(repository: ExamRepository, nav: androidx.navigation.NavHostController, attemptId: String) {
    val scope = rememberCoroutineScope()
    var bundle by remember { mutableStateOf<AttemptBundle?>(null) }
    var isBookmarked by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableLongStateOf(0L) }
    var showMap by remember { mutableStateOf(false) }
    var bookmarkedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSubmitConfirm by remember { mutableStateOf(false) }
    var loadingError by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        bundle = withContext(Dispatchers.IO) { repository.getAttemptBundle(attemptId) }
    }
    LaunchedEffect(attemptId) {
        try { reload() } catch (e: Exception) { loadingError = e.message }
    }
    val currentBundle = bundle
    val current = currentBundle?.questions?.getOrNull(currentBundle.attempt.currentPosition)

    LaunchedEffect(currentBundle?.attempt?.id, currentBundle?.attempt?.currentPosition, current?.questionDeadlineAtMillis, current?.selectedAnswer, current?.timeExpired) {
        val snapshot = bundle ?: return@LaunchedEffect
        if (snapshot.attempt.status != "IN_PROGRESS") return@LaunchedEffect
        while (isActive) {
            val now = System.currentTimeMillis()
            val activeQuestion = bundle?.questions?.getOrNull(bundle!!.attempt.currentPosition)
            val deadline = if (snapshot.attempt.timingMode == "OVERALL") snapshot.attempt.deadlineAtMillis else activeQuestion?.questionDeadlineAtMillis
            val shouldCountdown = snapshot.attempt.timingMode == "OVERALL" || (activeQuestion?.selectedAnswer == null && activeQuestion?.timeExpired == false)
            if (shouldCountdown && deadline != null) {
                val left = ((deadline - now) / 1_000L).coerceAtLeast(0L)
                remainingSeconds = left
                if (deadline <= now) {
                    if (snapshot.attempt.timingMode == "OVERALL") {
                        withContext(Dispatchers.IO) { repository.submitAttempt(attemptId) }
                        nav.navigate("result/$attemptId") { popUpTo("test/$attemptId") { inclusive = true } }
                    } else {
                        val last = withContext(Dispatchers.IO) { repository.expireCurrentQuestion(attemptId) }
                        if (last) {
                            withContext(Dispatchers.IO) { repository.submitAttempt(attemptId) }
                            nav.navigate("result/$attemptId") { popUpTo("test/$attemptId") { inclusive = true } }
                        } else reload()
                    }
                    break
                }
            } else if (snapshot.attempt.timingMode == "PER_QUESTION") {
                remainingSeconds = 0L
            }
            delay(300L)
        }
    }

    LaunchedEffect(current?.setId, current?.questionId) {
        isBookmarked = if (current == null) false else withContext(Dispatchers.IO) {
            repository.isBookmarked(current.setId, current.questionId)
        }
    }
    LaunchedEffect(showMap, currentBundle?.attempt?.setId) {
        if (showMap && currentBundle != null) {
            bookmarkedIds = withContext(Dispatchers.IO) { repository.getBookmarkedIds(currentBundle.attempt.setId) }
        }
    }

    if (loadingError != null) {
        Scaffold { Text(loadingError ?: "Unable to open test", Modifier.padding(24.dp)) }
        return
    }
    if (currentBundle == null || current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (currentBundle.attempt.status == "SUBMITTED") {
        LaunchedEffect(attemptId) { nav.navigate("result/$attemptId") { popUpTo("test/$attemptId") { inclusive = true } } }
        return
    }
    val a = currentBundle.attempt
    val answered = currentBundle.questions.count { it.selectedAnswer != null }
    val questionNumber = a.currentPosition + 1

    Scaffold(
        containerColor = Page,
        topBar = {
            TopAppBarSimple(
                a.testName,
                if (a.timingMode == "OVERALL") "Whole-test timer" else "Per-question timer"
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = Color.White) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { repository.moveTo(attemptId, (a.currentPosition - 1).coerceAtLeast(0)) }; reload() } }, enabled = a.currentPosition > 0, modifier = Modifier.weight(1f)) { Text("Previous") }
                    Button(onClick = {
                        if (a.currentPosition < a.questionCount - 1) {
                            scope.launch { withContext(Dispatchers.IO) { repository.moveTo(attemptId, a.currentPosition + 1) }; reload() }
                        } else showSubmitConfirm = true
                    }, modifier = Modifier.weight(1f)) { Text(if (a.currentPosition < a.questionCount - 1) "Next" else "Finish") }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Question $questionNumber of ${a.questionCount}", fontWeight = FontWeight.SemiBold, color = Navy)
                    Text("$answered answered · ${a.questionCount - answered} unanswered", color = Muted, fontSize = 12.sp)
                }
                Card(colors = CardDefaults.cardColors(containerColor = if (remainingSeconds <= 10) Color(0xFFFFE8E6) else Color.White)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TIME LEFT", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (a.timingMode == "PER_QUESTION" && current.selectedAnswer != null) "Answered" else formatCountdown(remainingSeconds),
                            color = if (remainingSeconds <= 10) Red else Navy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
            LinearProgressIndicator(progress = { answered.toFloat() / a.questionCount.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth(), color = Teal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showMap = true }, modifier = Modifier.weight(1f)) { Text("Question map") }
                OutlinedButton(onClick = { scope.launch { isBookmarked = withContext(Dispatchers.IO) { repository.toggleBookmark(current.setId, current.questionId) } } }, modifier = Modifier.weight(1f)) { Text(if (isBookmarked) "★ Bookmarked" else "☆ Bookmark") }
                OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { repository.toggleFlag(attemptId, a.currentPosition) }; reload() } }, modifier = Modifier.weight(1f)) { Text(if (current.flagged) "Flagged" else "Flag") }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(current.questionId, color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(current.question, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium, color = Navy)
                    LocalQuestionImage(current.imagePath)
                    val options = listOf("A" to current.optionA, "B" to current.optionB, "C" to current.optionC, "D" to current.optionD)
                    options.forEach { (key, label) ->
                        val selected = current.selectedAnswer == key
                        OutlinedButton(
                            onClick = { scope.launch { withContext(Dispatchers.IO) { repository.answerQuestion(attemptId, a.currentPosition, key) }; reload() } },
                            enabled = !current.timeExpired,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) Color(0xFFE4F2F0) else Color.White,
                                contentColor = if (selected) Teal else Navy
                            ),
                            border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Teal else Color(0xFFD6DEE7))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("$key.", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                Text(label, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                            }
                        }
                    }
                    if (current.selectedAnswer != null && !current.timeExpired) {
                        TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { repository.clearAnswer(attemptId, a.currentPosition) }; reload() } }) { Text("Clear response") }
                    }
                    if (current.timeExpired) Text("Time expired for this question. It can no longer be answered.", color = Red, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Flagged questions are reminders; bookmarks are saved to the separate review section.", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { showSubmitConfirm = true }) { Text("Submit test") }
            }
        }
    }

    if (showMap) {
        AlertDialog(
            onDismissRequest = { showMap = false },
            title = { Text("Question overview") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        LegendDot(Green, "Answered")
                        LegendDot(Muted, "Unanswered")
                        LegendDot(Amber, "Flagged")
                        LegendDot(Teal, "★ Saved")
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.heightIn(max = 320.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(currentBundle.questions) { index, q ->
                            val bg = when {
                                q.flagged -> Amber
                                q.selectedAnswer != null -> Green
                                else -> Color(0xFFE2E8F0)
                            }
                            Button(
                                onClick = { showMap = false; scope.launch { withContext(Dispatchers.IO) { repository.moveTo(attemptId, index) }; reload() } },
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = if (bg == Color(0xFFE2E8F0)) Navy else Color.White),
                                modifier = Modifier.aspectRatio(1f)
                            ) { Text("${index + 1}${if (q.questionId in bookmarkedIds) "★" else ""}", fontSize = 12.sp) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMap = false }) { Text("Close") } }
        )
    }
    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("Submit this mock test?") },
            text = { Text("${a.questionCount - answered} question(s) are unanswered. Once submitted, your answers cannot be changed. You can review the correct answers and explanations afterward.") },
            confirmButton = {
                TextButton(onClick = {
                    showSubmitConfirm = false
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.submitAttempt(attemptId) }
                        nav.navigate("result/$attemptId") { popUpTo("test/$attemptId") { inclusive = true } }
                    }
                }) { Text("Submit test") }
            },
            dismissButton = { TextButton(onClick = { showSubmitConfirm = false }) { Text("Continue test") } }
        )
    }
}

@Composable
private fun ResultScreen(repository: ExamRepository, nav: androidx.navigation.NavHostController, attemptId: String) {
    var bundle by remember { mutableStateOf<AttemptBundle?>(null) }
    LaunchedEffect(attemptId) { bundle = withContext(Dispatchers.IO) { repository.getAttemptBundle(attemptId) } }
    val value = bundle
    if (value == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val a = value.attempt
    Scaffold(containerColor = Page, topBar = { TopAppBarSimple("Test results", a.testName) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Navy)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("TOTAL SCORE", color = Color(0xFFC7D5E4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${fmt(a.totalScore)} / ${fmt(a.maxPossibleMarks)}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${fmt(a.accuracyPercent)}% accuracy · ${formatDuration(a.elapsedSeconds)}", color = Color(0xFFD8E4EF), modifier = Modifier.padding(top = 5.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Correct", a.correctCount.toString(), Green, Modifier.weight(1f))
                MetricCard("Incorrect", a.incorrectCount.toString(), Red, Modifier.weight(1f))
                MetricCard("Skipped", a.skippedCount.toString(), Muted, Modifier.weight(1f))
            }
            SectionCard("Score breakdown") {
                ScoreLine("Correct-answer marks", fmt(a.correctCount * a.positiveMarks), Green)
                ScoreLine("Skipped-question marks", fmt(a.skippedCount * a.skippedMarks), Green)
                ScoreLine("Total marks awarded", fmt(a.marksAwarded), Green)
                ScoreLine("Marks deducted", "−${fmt(a.marksDeducted)}", Red)
                ScoreLine("Net score", fmt(a.totalScore), Navy)
                ScoreLine("Time taken", formatDuration(a.elapsedSeconds), Navy)
                ScoreLine("Questions", a.questionCount.toString(), Navy)
                ScoreLine("Configuration", configSummary(a), Muted)
            }
            Text("Question-by-question review", color = Navy, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            value.questions.forEachIndexed { index, q ->
                ResultQuestionCard(index + 1, q, repository)
            }
            Button(onClick = { nav.navigate("library") { popUpTo("library") { inclusive = true } } }, modifier = Modifier.fillMaxWidth()) { Text("Back to question banks") }
        }
    }
}

@Composable
private fun ResultQuestionCard(number: Int, q: AttemptQuestionEntity, repository: ExamRepository) {
    val scope = rememberCoroutineScope()
    var bookmarked by remember { mutableStateOf(false) }
    LaunchedEffect(q.setId, q.questionId) { bookmarked = withContext(Dispatchers.IO) { repository.isBookmarked(q.setId, q.questionId) } }
    val status = when {
        q.selectedAnswer == null -> "Skipped"
        q.selectedAnswer == q.correctAnswer -> "Correct"
        else -> "Incorrect"
    }
    val color = when (status) { "Correct" -> Green; "Incorrect" -> Red; else -> Muted }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Q$number · ${q.questionId}", color = Navy, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(status.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(q.question, color = Navy, fontSize = 15.sp)
            LocalQuestionImage(q.imagePath)
            Text("Your answer: ${q.selectedAnswer ?: "Not answered"}${if (q.selectedAnswer == null) "" else " — ${optionText(q, q.selectedAnswer)}"}", color = color, fontSize = 13.sp)
            Text("Correct answer: ${q.correctAnswer} — ${optionText(q, q.correctAnswer)}", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("Explanation", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(q.explanation, color = Color(0xFF334155), fontSize = 13.sp, lineHeight = 19.sp)
            TextButton(onClick = { scope.launch { bookmarked = withContext(Dispatchers.IO) { repository.toggleBookmark(q.setId, q.questionId) } } }) { Text(if (bookmarked) "Remove bookmark" else "Bookmark question") }
        }
    }
}

@Composable
private fun HistoryScreen(repository: ExamRepository, nav: androidx.navigation.NavHostController) {
    var history by remember { mutableStateOf<List<TestAttemptEntity>>(emptyList()) }
    LaunchedEffect(Unit) { history = withContext(Dispatchers.IO) { repository.getHistory() } }
    Scaffold(containerColor = Page, topBar = { TopAppBarSimple("Test history", "Completed and in-progress tests") }) { padding ->
        if (history.isEmpty()) EmptyState("No tests in history", "Completed tests are stored on this device and can be reopened here.", Modifier.padding(padding).padding(16.dp))
        else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(history, key = { it.id }) { attempt ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Text(attempt.testName, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("${formatDate(attempt.startedAtMillis)} · ${attempt.setName}", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        if (attempt.status == "SUBMITTED") {
                            Text("${fmt(attempt.totalScore)} / ${fmt(attempt.maxPossibleMarks)} · ${attempt.correctCount} correct · ${attempt.incorrectCount} incorrect · ${attempt.skippedCount} skipped", color = Navy, fontSize = 13.sp)
                            Text("${fmt(attempt.accuracyPercent)}% accuracy · ${formatDuration(attempt.elapsedSeconds)}", color = Muted, fontSize = 12.sp)
                        } else Text("In progress · ${attempt.questionCount} questions", color = Amber, fontSize = 13.sp)
                        Button(onClick = { nav.navigate(if (attempt.status == "SUBMITTED") "result/${attempt.id}" else "test/${attempt.id}") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(if (attempt.status == "SUBMITTED") "Open results" else "Resume test")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewScreen(repository: ExamRepository, nav: androidx.navigation.NavHostController) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("BOOKMARKED") }
    var sets by remember { mutableStateOf<List<QuestionSetEntity>>(emptyList()) }
    var selectedSet by remember { mutableStateOf<String?>(null) }
    var questions by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
    var selectedQuestion by remember { mutableStateOf<QuestionEntity?>(null) }
    var sourceMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sets = withContext(Dispatchers.IO) { repository.getSets() } }
    LaunchedEffect(type, selectedSet) { questions = withContext(Dispatchers.IO) { repository.getReviewQuestions(type, selectedSet) } }

    Scaffold(containerColor = Page, topBar = { TopAppBarSimple("Question review", "Bookmarks and past responses") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(selected = type == "BOOKMARKED", onClick = { type = "BOOKMARKED" }, label = { Text("Saved") })
                FilterChip(selected = type == "INCORRECT", onClick = { type = "INCORRECT" }, label = { Text("Incorrect") })
                FilterChip(selected = type == "SKIPPED", onClick = { type = "SKIPPED" }, label = { Text("Skipped") })
            }
            Box {
                OutlinedButton(onClick = { sourceMenu = true }) { Text("Source: ${sets.firstOrNull { it.id == selectedSet }?.name ?: "All question banks"}") }
                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                    DropdownMenuItem(text = { Text("All question banks") }, onClick = { selectedSet = null; sourceMenu = false })
                    sets.forEach { set -> DropdownMenuItem(text = { Text(set.name) }, onClick = { selectedSet = set.id; sourceMenu = false }) }
                }
            }
            if (questions.isEmpty()) {
                EmptyState(
                    when (type) { "BOOKMARKED" -> "No bookmarked questions"; "INCORRECT" -> "No incorrect answers yet"; else -> "No skipped questions" },
                    when (type) { "BOOKMARKED" -> "Use the bookmark control during a test or in its results to save questions here."; else -> "Questions from submitted tests will appear here, grouped by their source question bank." }
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    items(questions, key = { it.setId + ":" + it.questionId }) { q ->
                        Card(onClick = { selectedQuestion = q }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text("${q.questionId} · ${sets.firstOrNull { it.id == q.setId }?.name ?: "Question bank"}", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(q.question, color = Navy, fontSize = 14.sp, maxLines = 3)
                                Text("Tap to review answer and explanation", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (selectedQuestion != null) {
        val q = selectedQuestion!!
        AlertDialog(
            onDismissRequest = { selectedQuestion = null },
            title = { Text("${q.questionId} · Review") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(q.question, fontWeight = FontWeight.Medium, color = Navy)
                    LocalQuestionImage(q.imagePath)
                    listOf("A" to q.optionA, "B" to q.optionB, "C" to q.optionC, "D" to q.optionD).forEach { (letter, option) ->
                        Text("$letter. $option", color = if (letter == q.correctAnswer) Green else Color(0xFF334155), fontSize = 13.sp)
                    }
                    Text("Correct answer: ${q.correctAnswer}", color = Green, fontWeight = FontWeight.Bold)
                    Text(q.explanation, color = Color(0xFF334155), fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedQuestion = null }) { Text("Close") }
            },
            dismissButton = {
                if (type == "BOOKMARKED") {
                    TextButton(onClick = {
                        // The bookmark is durable and independent of test history.
                        scope.launch {
                            withContext(Dispatchers.IO) { repository.toggleBookmark(q.setId, q.questionId) }
                            selectedQuestion = null
                            questions = withContext(Dispatchers.IO) { repository.getReviewQuestions(type, selectedSet) }
                        }
                    }) { Text("Remove bookmark") }
                }
            }
        )
    }
}

@Composable
private fun TopAppBarSimple(title: String, subtitle: String) {
    Surface(color = Color.White, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val filtered = new.filter { it.isDigit() || (decimal && it == '.') }
            if (filtered.count { it == '.' } <= 1) onValue(filtered)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ScoreLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 34.dp, bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Navy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 7.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun LocalQuestionImage(path: String?) {
    if (path.isNullOrBlank()) return
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) { bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) } }
    bitmap?.let {
        Image(bitmap = it.asImageBitmap(), contentDescription = "Question image", modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(9.dp).background(color, RoundedCornerShape(50)))
        Text(label, fontSize = 10.sp, color = Muted)
    }
}

private fun optionText(q: AttemptQuestionEntity, letter: String): String = when (letter) {
    "A" -> q.optionA; "B" -> q.optionB; "C" -> q.optionC; "D" -> q.optionD; else -> ""
}

private fun fmt(value: Double): String = String.format(Locale.getDefault(), "%.2f", value).trimEnd('0').trimEnd('.')
private fun formatCountdown(seconds: Long): String = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
private fun formatDuration(seconds: Long): String = if (seconds >= 3600) String.format(Locale.getDefault(), "%d h %02d min", seconds / 3600, (seconds % 3600) / 60) else String.format(Locale.getDefault(), "%d min %02d sec", seconds / 60, seconds % 60)
private fun formatDate(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
private fun configSummary(a: TestAttemptEntity): String = buildString {
    append(if (a.timingMode == "OVERALL") "Whole test: ${a.timeLimitSeconds / 60} min" else "Per question: ${a.timeLimitSeconds} sec")
    append(" · +${fmt(a.positiveMarks)}")
    append(" · ")
    append(if (a.negativeMarkingEnabled) "−${fmt(a.negativeMarks)}" else "negative marking off")
    append(" · skipped ${fmt(a.skippedMarks)}")
}
