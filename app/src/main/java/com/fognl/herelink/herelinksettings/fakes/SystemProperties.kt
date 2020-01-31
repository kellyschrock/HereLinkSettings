package com.fognl.herelink.herelinksettings.fakes

// Placeholder for all the SystemProperties stuff that isn't accessible from a user-space app
class SystemProperties {
    companion object {
        @JvmStatic
        fun get(prop: String, default: String): String {
            return default
        }

        @JvmStatic
        fun set(prop: String, value: String) {

        }

        fun whatTheFUCK() {}
    }
}
