package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDataDao {

    // insert or update step date for a specific date
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSteps(stepData: StepData)

    // Get step data for a specific date
    @Query("SELECT * FROM daily_steps WHERE date = :date LIMIT 1")
    suspend fun getStepsForDate(date: String): StepData?

    // Get all historical step data, ordered by date (most recent first)
    // Flow is used to observe changes in the data
    @Query("SELECT * FROM daily_steps ORDER BY date DESC")
    fun getAllSteps(): Flow<List<StepData>>

    // Get step data for a range of dates
    @Query("SELECT * FROM daily_steps WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getStepsForDateRange(startDate: String, endDate: String): Flow<List<StepData>>

    // Get total steps accumulated
    @Query("SELECT SUM(steps) FROM daily_steps")
    fun getTotalSteps(): Flow<Long>



}