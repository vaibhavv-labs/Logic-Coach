package com.example.data.model

data class Problem(
    val title: String,
    val description: String,
    val category: String,
    val difficulty: String
)

object PredefinedProblems {
    val list = listOf(
        Problem(
            title = "Sum of Two Numbers",
            description = "Write a program that takes two numbers as input and returns their sum. Try to think about how you store the values and combine them.",
            category = "Variables & Input",
            difficulty = "Easy"
        ),
        Problem(
            title = "Check Even or Odd",
            description = "Write a program that checks whether a given number is even or odd. Think about what math operator can help you check divisibility by 2.",
            category = "Conditionals (If-Else)",
            difficulty = "Easy"
        ),
        Problem(
            title = "Find the Largest of Three Numbers",
            description = "Write a program that takes three numbers as input and prints the largest one. How would you compare them step-by-step?",
            category = "Conditionals (If-Else)",
            difficulty = "Medium"
        ),
        Problem(
            title = "Count from 1 to N",
            description = "Write a program that takes a number N and prints all numbers from 1 to N. What concept allows you to repeat an action?",
            category = "Loops",
            difficulty = "Medium"
        ),
        Problem(
            title = "Sum of Elements in a List",
            description = "Write a program that takes a list of numbers and calculates the total sum of all elements. Imagine checking a row of numbers one by one and keeping a running total.",
            category = "Loops & Lists",
            difficulty = "Medium"
        ),
        Problem(
            title = "Reverse a String",
            description = "Write a program that takes a string (like 'hello') and reverses it (to 'olleh'). How would you access the characters starting from the end?",
            category = "Strings & Loops",
            difficulty = "Hard"
        ),
        Problem(
            title = "Check Prime Number",
            description = "Write a program to check if a given number is prime (only divisible by 1 and itself). How would you test divisors starting from 2?",
            category = "Loops & Logic",
            difficulty = "Hard"
        )
    )
}
