package com.r3.conclave.common.internal

class StateManager<S : Any>(initialState: S) {
    var state: S = initialState

    inline fun <reified T : S> checkStateIs(lazyMessage: () -> Any): T {
        return checkNotNull(state as? T, lazyMessage)
    }

    inline fun <reified T : S> checkStateIs(): T {
        return checkStateIs { "Expected state to be ${T::class.java.simpleName} but is ${state.javaClass.simpleName} instead." }
    }

    inline fun <reified T : S> transitionStateFrom(to: S): T {
        val state = checkStateIs<T>()
        this.state = to
        return state
    }

    inline fun <reified T : S> checkStateIsNot(lazyMessage: () -> Any): S {
        check(state !is T, lazyMessage)
        return state
    }

    inline fun <reified T : S> checkStateIsNot(): S {
        return checkStateIsNot<T>() { "Expected state to not be ${T::class.java.simpleName} but is." }
    }
}
