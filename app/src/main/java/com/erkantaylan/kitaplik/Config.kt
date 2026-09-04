package com.erkantaylan.kitaplik

import com.erkantaylan.kitaplik.catalog.HttpCatalogSource

/**
 * The dev library is served from the workstation and reached over the adb
 * reverse tunnel, so the device talks to localhost and nothing depends on the
 * machine's LAN address:
 *
 *   cd ~/Desktop/projects/reading/library && python3 -m http.server 8090
 *   adb reverse tcp:8090 tcp:8090
 */
const val DEV_LIBRARY_URL = "http://localhost:8090"

val catalogSource by lazy { HttpCatalogSource(DEV_LIBRARY_URL) }
