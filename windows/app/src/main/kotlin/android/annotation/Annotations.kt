package android.annotation
@Retention(AnnotationRetention.SOURCE) annotation class SuppressLint(vararg val value: String)
@Retention(AnnotationRetention.SOURCE) annotation class TargetApi(val value: Int)
@Retention(AnnotationRetention.SOURCE) annotation class RequiresPermission(vararg val value: String, val anyOf: Array<String> = [], val allOf: Array<String> = [])
