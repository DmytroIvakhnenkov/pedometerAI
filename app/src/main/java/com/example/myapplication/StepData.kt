package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_steps")
data class StepData (
    @PrimaryKey val date: String,
    val steps: Long
)