package nl.lrvdlinden.liftr.ui

import androidx.compose.runtime.mutableStateListOf
import nl.lrvdlinden.liftr.core.Exercise
import nl.lrvdlinden.liftr.core.GymTemplate
import nl.lrvdlinden.liftr.core.Muscle
import java.util.UUID

/** Screens shown on top of the tabs (sheets and pushed screens in the iOS app). */
sealed class Route {
    val key: String = UUID.randomUUID().toString()

    class WorkoutDetail(val id: String) : Route()
    object Badges : Route()
    class ExerciseDetail(val id: String) : Route()
    class ExerciseEdit(val id: String?, val initialName: String = "", val initialMuscle: Muscle? = null,
                       val onCreate: ((Exercise) -> Unit)? = null) : Route()
    class ExercisePicker(val onPick: (Exercise) -> Unit) : Route()
    class RoutineEdit(val id: String?) : Route()
    object Session : Route()
    class Summary(val workoutId: String) : Route()
    object Wallet : Route()
    class PassEdit(val id: String?) : Route()
    class PassFull(val id: String) : Route()
    class GymPicker(val onPick: (GymTemplate?) -> Unit) : Route()
    class Scanner(val onResult: (String, nl.lrvdlinden.liftr.core.BarcodeFormat) -> Unit) : Route()
    object Import : Route()
    object DataManagement : Route()
    object Privacy : Route()
    object BodyEntry : Route()
    object Onboarding : Route()
}

object Nav {
    val stack = mutableStateListOf<Route>()
    fun push(route: Route) { stack.add(route) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun remove(route: Route) { stack.remove(route) }
    fun popTo(predicate: (Route) -> Boolean) { while (stack.isNotEmpty() && !predicate(stack.last())) pop() }
    /** Replaces the top screen. */
    fun replace(route: Route) { pop(); push(route) }
}
