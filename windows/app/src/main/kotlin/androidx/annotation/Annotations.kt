package androidx.annotation
import kotlin.reflect.KClass
@Retention(AnnotationRetention.SOURCE) annotation class FloatRange(val from: Double = Double.NEGATIVE_INFINITY, val to: Double = Double.POSITIVE_INFINITY, val fromInclusive: Boolean = true, val toInclusive: Boolean = true)
@Retention(AnnotationRetention.SOURCE) annotation class IntRange(val from: Long = Long.MIN_VALUE, val to: Long = Long.MAX_VALUE)
@Retention(AnnotationRetention.SOURCE) annotation class RequiresApi(val value: Int = 1, val api: Int = 1)
@Retention(AnnotationRetention.SOURCE) annotation class OptIn(vararg val markerClass: KClass<out Annotation>)
@Retention(AnnotationRetention.SOURCE) annotation class DrawableRes
@Retention(AnnotationRetention.SOURCE) annotation class StringRes
@Retention(AnnotationRetention.SOURCE) annotation class PluralsRes
@Retention(AnnotationRetention.SOURCE) annotation class RawRes
@Retention(AnnotationRetention.SOURCE) annotation class ColorInt
@Retention(AnnotationRetention.SOURCE) annotation class ColorRes
@Retention(AnnotationRetention.SOURCE) annotation class ChecksSdkIntAtLeast(val api: Int = -1, val parameter: Int = -1, val lambda: Int = -1, val codename: String = "")
@Retention(AnnotationRetention.SOURCE) annotation class MainThread
@Retention(AnnotationRetention.SOURCE) annotation class UiThread
@Retention(AnnotationRetention.SOURCE) annotation class WorkerThread
@Retention(AnnotationRetention.SOURCE) annotation class AnyThread
@Retention(AnnotationRetention.SOURCE) annotation class VisibleForTesting(val otherwise: Int = 2)
@Retention(AnnotationRetention.SOURCE) annotation class Keep
@Retention(AnnotationRetention.SOURCE) annotation class GuardedBy(val value: String)
@Retention(AnnotationRetention.SOURCE) annotation class CallSuper
@Retention(AnnotationRetention.SOURCE) annotation class Px
@Retention(AnnotationRetention.SOURCE) annotation class Size(val value: Long = -1, val min: Long = Long.MIN_VALUE, val max: Long = Long.MAX_VALUE, val multiple: Long = 1)
@Retention(AnnotationRetention.SOURCE) annotation class RestrictTo(vararg val value: Scope) { enum class Scope { LIBRARY, LIBRARY_GROUP, LIBRARY_GROUP_PREFIX, TESTS, SUBCLASSES } }
