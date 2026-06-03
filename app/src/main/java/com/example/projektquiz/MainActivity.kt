package com.example.projektquiz

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.projektquiz.ui.theme.ProjektQuizTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.io.InputStreamReader
import kotlin.collections.forEachIndexed


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProjektQuizTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    QuizAppNavHost(navController)
                }
            }
        }
    }
}

@Composable
fun QuizAppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = "menu") {
        composable("menu") { QuizMenu(navController) }
        composable("quizSoloList") { QuizListScreen(navController) }
        composable("multiplayer") { QuizListScreenMultiplayer(navController) }

        composable("lobby_host/{quizId}") { backStackEntry ->
            val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
            LobbyHostScreen(quizId = quizId, navController = navController)
        }

        composable("lobby_join") {
            LobbyJoinScreen(navController = navController)
        }
        composable(
            route = "quiz_screen/{lobbyId}?playerName={playerName}",
            arguments = listOf(
                navArgument("lobbyId") { type = NavType.StringType },
                navArgument("playerName") {
                    type = NavType.StringType
                    defaultValue = "Unknown"
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val lobbyId = backStackEntry.arguments?.getString("lobbyId") ?: ""
            val playerName = backStackEntry.arguments?.getString("playerName") ?: "Unknown"
            QuizScreenMultiplayer(navController, lobbyId, playerName)
        }
        composable("results_screen/{lobbyId}") { backStackEntry ->
            val lobbyId = backStackEntry.arguments?.getString("lobbyId") ?: return@composable
            ResultsScreen(navController, lobbyId)
        }

        composable("quiz_list") {
            QuizListScreen(navController)
        }
        composable("quiz/{quizId}") { backStackEntry ->
            val quizId = backStackEntry.arguments?.getString("quizId")
            if (quizId != null) {
                QuizScreen(quizId = quizId, navController = navController)
            } else {
                // Możesz pokazać ekran błędu lub przekierować
                Text("Błąd: quizId jest null")
            }
        }

        composable("solo_result/{score}/{correctCount}/{total}") { backStackEntry ->
            val score = backStackEntry.arguments?.getString("score")?.toDoubleOrNull() ?: 0.0
            val correctCount = backStackEntry.arguments?.getString("correctCount")?.toIntOrNull() ?: 0
            val total = backStackEntry.arguments?.getString("total")?.toIntOrNull() ?: 0
            SoloResultScreen(score, correctCount, total, navController)
        }


    }
}

data class Quiz(
    var id: String = "",
    var title: String = "",
    var imageName: String? = null,
    var questions: List<Question> = emptyList()
) {
    // Pusty konstruktor wymagany przez Firestore
    constructor() : this("", "", null, emptyList())
}

data class QuizWithImage(
    val id: String,
    val title: String,
    val imageResId: Int
)

data class Question(
    var question: String = "",
    var answers: List<String> = emptyList(),
    val correctAnswer: String = ""
) {
    constructor() : this("", emptyList(),"")
}

fun loadQuizzesFromAssets(context: Context): List<Quiz> {
    return try {
        val inputStream = context.assets.open("quizy.json")
        val reader = InputStreamReader(inputStream)
        val quizListType = object : TypeToken<List<Quiz>>() {}.type
        Gson().fromJson(reader, quizListType)
    } catch (e: Exception) {
        Log.e("QuizLoader", "Error loading quizzes from JSON", e)
        emptyList()
    }
}

fun loadQuizzesFromDatabase(onResult:  (List<Quiz>) -> Unit) {
    val db = FirebaseFirestore.getInstance()

    db.collection("quizy")
        .get()
        .addOnSuccessListener { result ->
            val quizzes = result.map { document ->
                Quiz(
                    id = document.id,
                    title = document.getString("title") ?: "Bez tytułu",
                    questions = document.get("questions") as List<Question>,
                    imageName = document.getString("imageName")  // Jeśli jest przypisany obrazek
                )
            }
            onResult(quizzes)
        }
        .addOnFailureListener { exception ->
            Log.w("QuizListScreen", "Error getting documents.", exception)
            onResult(emptyList())
        }
}

fun mapToQuizWithImage(quizzes: List<Quiz>, context: Context): List<QuizWithImage> {
    return quizzes.map { quiz ->
        val imageResId = quiz.imageName?.let {
            getImageResIdByName(context, it)
        } ?: R.drawable.quiz_default_icon  // Domyślny obrazek, jeśli brak obrazka

        QuizWithImage(
            id = quiz.id,
            title = quiz.title,
            imageResId = imageResId
        )
    }
}

fun getImageResIdByName(context: Context, imageName: String): Int {
    return context.resources.getIdentifier(imageName, "drawable", context.packageName)
}


@Composable
fun QuizMenu(navController: NavHostController, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier = modifier
        .fillMaxSize()
        .background(Color(0xFF4396D5)),
        //verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.title),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 100.dp)

        )
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = { navController.navigate("quizSoloList") },
            modifier = Modifier
                .width(200.dp)
                .height(60.dp)
                .border(
                    width = 2.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(32.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5243A4),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Tryb Solo",
                fontSize = 20.sp
                )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {navController.navigate("multiplayer")},
            modifier = Modifier
                .width(200.dp)
                .height(60.dp)
                .border(
                    width = 2.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(32.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5243A4),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Tryb Multiplayer",
                fontSize = 20.sp
            )
        }
    }
}


@Composable
fun QuizListScreen(navController: NavController, modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current
    var quizzes by remember { mutableStateOf<List<Quiz>>(emptyList()) }
    var expandedStates by remember { mutableStateOf<List<Boolean>>(emptyList()) }

    LaunchedEffect(Unit) {
        val loadedQuizzes = loadQuizzesFromAssets(context)
        quizzes = loadedQuizzes
        expandedStates = List(loadedQuizzes.size) { false }
    }

    Box {
        Column(modifier = modifier) {
            // Górny pasek z tytułem i przyciskiem powrotu
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF5243A4))
                    .padding(top = 48.dp, bottom = 16.dp)
            ) {
                Button(
                    onClick = { navController.navigate("menu") },
                    modifier = Modifier.align(Alignment.CenterStart),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5243A4))
                ) {
                    Image(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = "Wybierz quiz",
                    fontSize = 30.sp,
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                itemsIndexed(quizzes) { index, quiz ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(Color(0xFFE0E0E0), shape = RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Button(
                            onClick = {
                                expandedStates = expandedStates.toMutableList().apply {
                                    this[index] = !this[index]
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4298D5)),
                            shape = RectangleShape
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = quiz.title,
                                    fontSize = 20.sp,
                                    color = Color.White,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                                Image(
                                    painter = painterResource(
                                        id = if (expandedStates[index]) R.drawable.arrow_back else R.drawable.arrow_forward
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.CenterEnd)
                                )
                            }
                        }

                        if (expandedStates[index]) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        navController.navigate("quiz/${quiz.id}")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .padding(bottom = 8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2B8F55),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Graj")
                                }
                                /*Button(
                                    onClick = { /* Edytuj quiz */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Gray,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Edytuj")
                                }
                                Button(
                                    onClick = { /* Usuń quiz */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Red,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Usuń")
                                }*/
                            }
                        }
                    }
                }
            }
        }

        // Dodaj quiz
        /*Button(
            onClick = { /* Dodaj nowy quiz */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .size(68.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
        ) {
            Image(
                painter = painterResource(R.drawable.add),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }*/
    }
}

@Composable
fun QuizScreen(quizId: String, navController: NavController) {
    val context = LocalContext.current
    val questions = remember { mutableStateListOf<Question>() }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var showResult by remember { mutableStateOf(false) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var isCorrectAnswer by remember { mutableStateOf<Boolean?>(null) }
    var answerClicked by remember { mutableStateOf(false) }
    val startTime = remember { System.currentTimeMillis() }
    var correctCount by remember { mutableStateOf(0) }

    // Ładowanie quizu z pliku JSON po quizId
    LaunchedEffect(quizId) {
        val quizList = loadQuizzesFromAssets(context)
        val quiz = quizList.find { it.id == quizId }
        quiz?.questions?.forEach { q ->
            questions.add(
                Question(
                    question = q.question,
                    answers = q.answers.shuffled(),
                    correctAnswer = q.correctAnswer
                )
            )
        }
    }

    if (questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentQuestion = questions[currentQuestionIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF4396D5).copy(alpha = 0.8f))
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Pytanie ${currentQuestionIndex + 1} z ${questions.size}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = currentQuestion.question,
            fontSize = 24.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(24.dp))

        currentQuestion.answers.forEach { answer ->
            val backgroundColor = when {
                selectedAnswer == null -> Color(0xFF5243A4).copy(alpha = 0.9f)
                answer == selectedAnswer -> if (isCorrectAnswer == true) Color(0xFFAAF683) else Color(0xFFFF686B)
                else -> Color.LightGray
            }

            Button(
                onClick = {
                    if (selectedAnswer == null) {
                        selectedAnswer = answer
                        isCorrectAnswer = (answer == currentQuestion.correctAnswer)
                        answerClicked = true
                        if (isCorrectAnswer == true) correctCount++
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(backgroundColor),
                enabled = selectedAnswer == null,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text(answer)
            }
        }
    }

    LaunchedEffect(answerClicked) {
        if (answerClicked) {
            delay(1500)
            if (currentQuestionIndex < questions.lastIndex) {
                currentQuestionIndex++
                selectedAnswer = null
                isCorrectAnswer = null
                answerClicked = false
            } else {
                showResult = true
            }
        }
    }

    LaunchedEffect(showResult) {
        if (showResult) {
            val endTime = System.currentTimeMillis()
            val totalTimeSeconds = (endTime - startTime) / 1000.0
            val finalScore = if (totalTimeSeconds > 0) (correctCount / totalTimeSeconds) * 10 else 0.0

            navController.navigate("solo_result/${finalScore}/${correctCount}/${questions.size}")
        }
    }
}

@Composable
fun SoloResultScreen(score: Double, correctCount: Int, total: Int, navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Wynik końcowy", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Poprawne odpowiedzi: $correctCount z $total")
        Text(String.format("Twój wynik: %.2f", score))
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            navController.navigate("quiz_list") {
                popUpTo("quiz_list") { inclusive = true }
            }
        }) {
            Text("Powrót do listy quizów")
        }
    }
}



@Composable
fun QuizListScreenMultiplayer(navController: NavController, modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current
    var quizzes by remember { mutableStateOf<List<QuizWithImage>>(emptyList()) }
    var expandedStates by remember { mutableStateOf<List<Boolean>>(emptyList()) }


    // Ładujemy quizy z Firestore
    LaunchedEffect(true) {
        loadQuizzesFromDatabase { loadedQuizzes ->
            quizzes = mapToQuizWithImage(loadedQuizzes, context)
            expandedStates = List(loadedQuizzes.size) { false }
        }
    }

    Box {
        Column(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF5243A4))
                    .padding(top = 48.dp, bottom = 16.dp)
            ) {
                // Przycisk powrotu
                Button(
                    onClick = { navController.navigate("menu") },
                    modifier = Modifier.align(Alignment.CenterStart),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5243A4)
                    )
                ) {
                    Image(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Tytuł na środku
                Text(
                    text = "Wybierz quiz",
                    fontSize = 30.sp,
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            // ...

            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                itemsIndexed(quizzes) { index, quiz ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(Color(0xFFE0E0E0), shape = RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Button(
                            onClick = {
                                expandedStates = expandedStates.toMutableList().also { it[index] = !it[index] }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4298D5)),
                            shape = RectangleShape
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                // Załaduj obrazek z zasobów
                                Image(
                                    painter = painterResource(id = quiz.imageResId),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .align(Alignment.CenterStart)
                                )

                                Text(
                                    text = quiz.title,
                                    fontSize = 20.sp,
                                    color = Color.White,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                                Image(
                                    painter = painterResource(
                                        id = if (expandedStates[index]) R.drawable.arrow_back else R.drawable.arrow_forward
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.CenterEnd)
                                )
                            }
                        }
                        if (expandedStates[index]) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { navController.navigate("lobby_host/${quiz.id}") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2B8F55),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Rozpocznij")
                                }
                                Button(
                                    onClick = { navController.navigate("lobby_join") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Gray,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Dołącz")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Przycisk na dole
        /*Button(
            onClick = {/* Dodawanie nowego quizu online */},
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .width(68.dp)
                .height(68.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Gray
            )
        ) {
            Image(
                painter = painterResource(R.drawable.add),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }*/
    }
}

@Composable
fun LobbyHostScreen(navController: NavController, quizId: String) {
    val db = Firebase.firestore
    val context = LocalContext.current
    val lobbyId = remember { UUID.randomUUID().toString().take(6).uppercase() }
    var nickname by remember { mutableStateOf("") }
    var players by remember { mutableStateOf<List<String>>(emptyList()) }
    var isHostJoined by remember { mutableStateOf(false) }
    val lobbyCreated = remember { mutableStateOf(false) }

    // Tworzenie lobby raz
    LaunchedEffect(lobbyCreated.value) {
        if (!lobbyCreated.value) {
            db.collection("lobbies").document(lobbyId)
                .set(mapOf("quizId" to quizId, "players" to listOf<String>(), "started" to false))
            lobbyCreated.value = true
        }
    }

    // Nasłuchuj graczy
    LaunchedEffect(lobbyId) {
        db.collection("lobbies").document(lobbyId)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    val playerList = it["players"] as? List<String> ?: emptyList()
                    players = playerList
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text("Twój kod lobby: $lobbyId", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Twój nick") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (nickname.isNotBlank()) {
                    db.collection("lobbies").document(lobbyId)
                        .update("players", FieldValue.arrayUnion(nickname))
                        .addOnSuccessListener { isHostJoined = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dołącz jako host")
        }

        if (isHostJoined) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Dołączyłaś jako host!", color = Color.Green)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Gracze w lobby:", fontWeight = FontWeight.Bold)

        LazyColumn {
            items(players) { player ->
                Text(text = player)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                db.collection("lobbies")
                    .document(lobbyId)
                    .update("started", true)
                navController.navigate("quiz_screen/$lobbyId?playerName=$nickname")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Rozpocznij grę")
        }
    }
}

@Composable
fun LobbyJoinScreen(navController: NavController) {
    val db = Firebase.firestore
    var code by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var isJoined by remember { mutableStateOf(false) }

    // Nasłuch na start gry
    LaunchedEffect(code, isJoined) {
        if (isJoined && code.isNotBlank()) {
            db.collection("lobbies")
                .document(code)
                .addSnapshotListener { snapshot, _ ->
                    val started = snapshot?.getBoolean("started") ?: false
                    if (started) {
                        navController.navigate("quiz_screen/$code?playerName=$nickname")
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Kod lobby") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Twój nick") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (nickname.isNotBlank() && code.isNotBlank()) {
                    db.collection("lobbies").document(code)
                        .update("players", FieldValue.arrayUnion(nickname))
                        .addOnSuccessListener { isJoined = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dołącz do lobby")
        }

        if (isJoined) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Dołączyłaś do lobby!", color = Color.Green)
            Text("Czekaj na rozpoczęcie gry...", fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
fun QuizScreenMultiplayer(navController: NavController, lobbyId: String, playerName: String) {
    val db = Firebase.firestore
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var showResult by remember { mutableStateOf(false) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var isCorrectAnswer by remember { mutableStateOf<Boolean?>(null) }
    var answerClicked by remember { mutableStateOf(false) }
    val startTime = remember { System.currentTimeMillis() }
    var correctCount by remember { mutableStateOf(0) }


    // Ładowanie quizu z Firestore
    LaunchedEffect(lobbyId) {
        db.collection("lobbies").document(lobbyId).get()
            .addOnSuccessListener { lobbySnapshot ->
                val quizId = lobbySnapshot.getString("quizId")
                if (quizId != null) {
                    db.collection("quizy").document(quizId).get()
                        .addOnSuccessListener { quizSnapshot ->
                            val questionsList = quizSnapshot.get("questions") as? List<Map<String, Any>>
                            Log.d("QuizScreenMulitiplayer", "Pobrane pytania: $questionsList")

                            val parsedQuestions = questionsList?.mapNotNull { value ->
                                val question = value["question"] as String
                                val shuffledAnswers = (value["answers"] as List<*>).filterIsInstance<String>().shuffled() ?: emptyList()
                                val correctAnswer = value["correctAnswer"] as String

                                Question(
                                    question = question,
                                    answers = shuffledAnswers,
                                    correctAnswer = correctAnswer
                                )
                            } ?: emptyList()

                            questions = parsedQuestions
                        }
                        .addOnFailureListener {
                            Log.e("QuizScreenMulitiplayer", "Błąd wczytywania quizu: ${it.message}")
                        }
                } else {
                    Log.e("QuizScreenMulitiplayer", "quizId == null")
                }
            }
            .addOnFailureListener {
                Log.e("QuizScreenMulitiplayer", "Błąd wczytywania lobby: ${it.message}")
            }
    }

    // Ekran ładowania
    if (questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Wyświetlanie pytania
    val currentQuestion = questions[currentQuestionIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Gracz: $playerName", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = currentQuestion.question, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))

        currentQuestion.answers.forEach { answer ->
            val backgroundColor = when {
                selectedAnswer == null -> Color.LightGray
                answer == selectedAnswer -> {
                    if (isCorrectAnswer == true) {
                        Log.d("KolorDebug", "Zielony dla $answer")
                        Color(0xFFAAF683)
                    } else {
                        Log.d("KolorDebug", "Czerwony dla $answer")
                        Color(0xFFFF686B)
                    }
                }
                else -> {
                    Log.d("KolorDebug", "Szary dla $answer")
                    Color.Gray.copy(alpha = 0.4f)
                }
            }


            Button(
                onClick = {
                    if (selectedAnswer == null) {
                        selectedAnswer = answer
                        isCorrectAnswer = (answer == currentQuestion.correctAnswer)
                        answerClicked = true
                        if (answer == currentQuestion.correctAnswer) {
                            correctCount++
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(backgroundColor), // ręczne tło
                enabled = selectedAnswer == null,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent) // bez domyślnego koloru
            ) {
                Text(answer)
            }

        }

        if (showResult) {
            val endTime = System.currentTimeMillis()
            val totalTimeSeconds = (endTime - startTime) / 1000.0
            val finalScore = if (totalTimeSeconds > 0) (correctCount / totalTimeSeconds) * 10 else 0.0

            val resultData = mapOf(
                "playerName" to playerName,
                "correctCount" to correctCount,
                "timeSeconds" to totalTimeSeconds,
                "score" to finalScore,
                "finished" to true
            )

            db.collection("lobbies")
                .document(lobbyId)
                .collection("results")
                .document(playerName)
                .set(resultData)
        }
    }
    LaunchedEffect(answerClicked) {
        if (answerClicked) {
            delay(1500)
            if (currentQuestionIndex < questions.lastIndex) {
                currentQuestionIndex++
                selectedAnswer = null
                isCorrectAnswer = null
                answerClicked = false
            } else {
                showResult = true
            }
        }
    }
    LaunchedEffect(showResult) {
        if (showResult) {
            val lobbyRef = db.collection("lobbies").document(lobbyId)

            // Pobierz listę graczy z głównego dokumentu
            lobbyRef.get().addOnSuccessListener { snapshot ->
                val playerList = snapshot.get("players") as? List<String> ?: return@addOnSuccessListener

                lobbyRef.collection("results")
                    .addSnapshotListener { querySnapshot, _ ->
                        val finishedCount = querySnapshot?.documents?.count { it.getBoolean("finished") == true } ?: 0
                        if (finishedCount == playerList.size) {
                            // Wszyscy zakończyli – pokaż tablicę wyników
                            navController.navigate("results_screen/$lobbyId")
                        }
                    }
            }
        }
    }
}

@Composable
fun ResultsScreen(navController: NavController, lobbyId: String) {
    val db = Firebase.firestore
    var results by remember { mutableStateOf<List<PlayerResult>>(emptyList()) }

    LaunchedEffect(lobbyId) {
        db.collection("lobbies")
            .document(lobbyId)
            .collection("results")
            .get()
            .addOnSuccessListener { snapshot ->
                val parsedResults = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("playerName") ?: return@mapNotNull null
                    val correct = doc.getLong("correctCount")?.toInt() ?: 0
                    val time = doc.getDouble("timeSeconds") ?: 0.0
                    val score = doc.getDouble("score") ?: 0.0
                    PlayerResult(name, correct, time, score)
                }.sortedByDescending { it.score }

                results = parsedResults
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text("Wyniki quizu", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (results.isEmpty()) {
            CircularProgressIndicator()
        } else {
            results.forEachIndexed { index, result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("#${index + 1}. ${result.name}")
                        Text("✓ ${result.correctCount} / ${String.format("%.1fs", result.timeSeconds)}")
                        Text("Score: ${String.format("%.1f", result.score)}")
                    }
                }
            }
        }
        Button(
            onClick = { navController.navigate("menu") },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Powrót do menu")
        }
    }
}



data class PlayerResult(
    val name: String,
    val correctCount: Int,
    val timeSeconds: Double,
    val score: Double
)


@Preview(showBackground = true)
@Composable
fun QuizMenuPreview() {
    ProjektQuizTheme {
        val navController = rememberNavController()
        QuizListScreen(navController)
    }
}