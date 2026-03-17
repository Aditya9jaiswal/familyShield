package com.example.familyshield.utils

object Constants {
    const val DB_ROOT = "familyshield"
    const val DB_ADMINS = "admins"
    const val DB_USERS = "users"
    const val DB_COMMANDS = "commands"
    const val DB_COMPLAINTS = "complaints"

    const val CMD_PENDING = "pending"
    const val CMD_PROCESSING = "processing"
    const val CMD_EXECUTED = "executed"
    const val CMD_FAILED = "failed"

    const val CMD_LOCK = "LOCK"
    const val CMD_LOCATE = "LOCATE"
    const val CMD_SIREN_ON = "SIREN_ON"
    const val CMD_SIREN_OFF = "SIREN_OFF"
    const val CMD_FACTORY_RESET = "FACTORY_RESET"
    const val CMD_ENABLE_RESET = "ENABLE_RESET"
    const val CMD_DISABLE_RESET = "DISABLE_RESET"

    const val COMPLAINT_FILED = "filed"
    const val COMPLAINT_PROCESSING = "processing"
    const val COMPLAINT_RESOLVED = "resolved"

    const val EXTRA_ADMIN_MOBILE = "admin_mobile"
    const val EXTRA_USER_MOBILE = "user_mobile"
}