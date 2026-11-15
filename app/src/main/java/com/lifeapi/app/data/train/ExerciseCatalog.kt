package com.lifeapi.app.data.train

import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Exercise
import com.lifeapi.app.data.TrainDao

val MUSCLES = listOf("Chest", "Back", "Shoulders", "Legs", "Arms", "Core")
val EQUIPMENT = listOf("Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight")

private fun ex(name: String, muscle: String, eq: String, kind: String = "kg × reps") = Exercise(name, muscle, eq, kind)

/** A sensible starter library; anything the user types that isn't here gets created inline. */
val DEFAULT_EXERCISES = listOf(
    ex("Bench press", "Chest", "Barbell"),
    ex("Incline bench press", "Chest", "Barbell"),
    ex("Incline DB press", "Chest", "Dumbbell"),
    ex("DB bench press", "Chest", "Dumbbell"),
    ex("Cable fly", "Chest", "Cable"),
    ex("Pec deck", "Chest", "Machine"),
    ex("Dips", "Chest", "Bodyweight"),
    ex("Push-ups", "Chest", "Bodyweight", "reps"),
    ex("Deadlift", "Back", "Barbell"),
    ex("Barbell row", "Back", "Barbell"),
    ex("Pull-up", "Back", "Bodyweight"),
    ex("Weighted pull-up", "Back", "Bodyweight"),
    ex("Chin-up", "Back", "Bodyweight"),
    ex("Lat pulldown", "Back", "Cable"),
    ex("Seated cable row", "Back", "Cable"),
    ex("Chest-supported row", "Back", "Dumbbell"),
    ex("One-arm row", "Back", "Dumbbell"),
    ex("Overhead press", "Shoulders", "Barbell"),
    ex("DB shoulder press", "Shoulders", "Dumbbell"),
    ex("Arnold press", "Shoulders", "Dumbbell"),
    ex("Lateral raise", "Shoulders", "Dumbbell"),
    ex("Cable lateral raise", "Shoulders", "Cable"),
    ex("Face pull", "Shoulders", "Cable"),
    ex("Rear delt fly", "Shoulders", "Dumbbell"),
    ex("Back squat", "Legs", "Barbell"),
    ex("Front squat", "Legs", "Barbell"),
    ex("Romanian deadlift", "Legs", "Barbell"),
    ex("Leg press", "Legs", "Machine"),
    ex("Bulgarian split squat", "Legs", "Dumbbell"),
    ex("Walking lunge", "Legs", "Dumbbell"),
    ex("Leg extension", "Legs", "Machine"),
    ex("Leg curl", "Legs", "Machine"),
    ex("Hip thrust", "Legs", "Barbell"),
    ex("Calf raise", "Legs", "Machine"),
    ex("Biceps curl", "Arms", "Dumbbell"),
    ex("Barbell curl", "Arms", "Barbell"),
    ex("Hammer curl", "Arms", "Dumbbell"),
    ex("Triceps pushdown", "Arms", "Cable"),
    ex("Skull crusher", "Arms", "Barbell"),
    ex("Overhead triceps extension", "Arms", "Cable"),
    ex("Plank", "Core", "Bodyweight", "time"),
    ex("Hanging leg raise", "Core", "Bodyweight", "reps"),
    ex("Cable crunch", "Core", "Cable"),
    ex("Ab wheel", "Core", "Bodyweight", "reps"),
)

object ExerciseCatalog {
    /** Seed the library once, and make sure every name already used in sets/templates is in it. */
    suspend fun ensureSeeded(dao: TrainDao) {
        if (dao.exerciseCount() == 0) dao.insertExercises(DEFAULT_EXERCISES)
        val known = DEFAULT_EXERCISES.associateBy { it.name.lowercase() }
        dao.insertExercises(dao.usedExerciseNames().map { n -> known[n.lowercase()]?.copy(name = n) ?: Exercise(n, createdTs = Days.now()) })
    }

    fun normalize(name: String): String = name.trim().replace(Regex("\\s+"), " ").replaceFirstChar { it.uppercase() }
}
