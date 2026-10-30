package dev.horizon.client.module

/** Every module, in registration order (the search lists results in this order). */
object Modules {
    val all = ArrayList<Module>()

    fun register(m: Module) {
        all += m
    }

    inline fun <reified T : Module> get(): T = all.first { it is T } as T
}
