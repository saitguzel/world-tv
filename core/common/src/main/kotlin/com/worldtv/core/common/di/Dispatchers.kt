package com.worldtv.core.common.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

/** Application-scoped scope for work that must outlive a screen (playback reports). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
