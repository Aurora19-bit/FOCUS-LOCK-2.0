package com.vidhya.focuslock

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

/**
 * Gate screen for uninstalling FocusLock. Requires REQUIRED_STREAK correct
 * answers in a row, with difficulty increasing as you go. A wrong answer
 * resets the streak to zero and generates a new problem — this is deliberate,
 * so it can't be brute-forced by guessing repeatedly on the same question.
 * Cannot be reached while a focus session is active (MainActivity enforces
 * this before launching it), and blocked apps/Settings can't touch it either
 * since it lives inside FocusLock itself.
 */
class MathPuzzleActivity : AppCompatActivity() {

    private var streak = 0
    private var answer = 0
    private val requiredStreak = 5

    private lateinit var questionView: TextView
    private lateinit var progressView: TextView
    private lateinit var feedbackView: TextView
    private lateinit var answerInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_puzzle)

        questionView = findViewById(R.id.puzzleQuestion)
        progressView = findViewById(R.id.puzzleProgress)
        feedbackView = findViewById(R.id.puzzleFeedback)
        answerInput = findViewById(R.id.puzzleAnswer)

        findViewById<Button>(R.id.puzzleSubmit).setOnClickListener { checkAnswer() }
        findViewById<Button>(R.id.puzzleCancel).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        nextQuestion()
    }

    private fun nextQuestion() {
        answerInput.setText("")
        feedbackView.text = ""
        progressView.text = "Correct in a row: $streak / $requiredStreak"

        // Difficulty ramps with streak: bigger numbers and more operations
        // the closer you get, so it takes sustained attention, not luck.
        val range = 10 + streak * 8
        val a = Random.nextInt(1, range)
        val b = Random.nextInt(1, range)
        val c = Random.nextInt(1, 10)

        val useThreeTerms = streak >= 2
        val op1 = listOf("+", "-", "\u00D7").random()

        if (!useThreeTerms) {
            answer = when (op1) {
                "+" -> a + b
                "-" -> a - b
                else -> a * b
            }
            questionView.text = "$a $op1 $b = ?"
        } else {
            val op2 = listOf("+", "-").random()
            val firstResult = when (op1) {
                "+" -> a + b
                "-" -> a - b
                else -> a * b
            }
            answer = when (op2) {
                "+" -> firstResult + c
                else -> firstResult - c
            }
            questionView.text = "$a $op1 $b $op2 $c = ?"
        }
    }

    private fun checkAnswer() {
        val input = answerInput.text.toString().toIntOrNull()
        if (input == null) {
            feedbackView.text = "Enter a number"
            return
        }
        if (input == answer) {
            streak++
            if (streak >= requiredStreak) {
                setResult(Activity.RESULT_OK)
                finish()
                return
            }
            feedbackView.text = ""
        } else {
            streak = 0
            feedbackView.text = "Wrong — streak reset"
        }
        nextQuestion()
    }

    // Disable back button so it can't be dismissed to skip the gate silently;
    // use the explicit Cancel button instead, which is an honest opt-out.
    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
