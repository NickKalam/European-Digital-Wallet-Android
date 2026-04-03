package com.digitalwallet.model

import androidx.room.TypeConverter

enum class DocumentType { ID_CARD, DRIVER_LICENSE, TICKET }
enum class DocumentStatus { VALID, EXPIRED, REVOKED }
enum class AuthStatus { SUCCESS, FAILED }


class Converters {
    @TypeConverter
    fun toDocumentType(value: String) = enumValueOf<DocumentType>(value)
    @TypeConverter
    fun fromDocumentType(value: DocumentType) = value.name

    @TypeConverter
    fun toDocumentStatus(value: String) = enumValueOf<DocumentStatus>(value)
    @TypeConverter
    fun fromDocumentStatus(value: DocumentStatus) = value.name

    /*@TypeConverter fun toAuthStatus(value: String) = enumValueOf<AuthStatus>(value)
    @TypeConverter fun fromAuthStatus(value: AuthStatus) = value.name
    */

}