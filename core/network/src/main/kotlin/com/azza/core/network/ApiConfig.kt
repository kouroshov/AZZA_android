package com.azza.core.network

import javax.inject.Qualifier

/**
 * apps/api's base URL, ending in `/api/v1/`. Bound by :app from its
 * BuildConfig (debug: local.properties `azza.apiBaseUrl`, default the
 * emulator's 10.0.2.2), so this module never hard-codes an environment.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiBaseUrl

/** "android/<versionName>" — sent as the User-Agent, so sessions are recognisable in the admin panel. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClientUserAgent

/** Used by the refresh call only: a client with no auth interceptor and no authenticator. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class BareClient
