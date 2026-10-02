package com.afede.kidago

import android.app.Application

class KidaGoApp : Application() {
    val container by lazy { AppContainer(this) }
}
