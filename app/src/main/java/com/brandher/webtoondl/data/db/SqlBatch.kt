package com.brandher.webtoondl.data.db

/**
 * Límites compartidos para operaciones SQLite inline (cláusulas `IN`).
 * SQLite tiene 999 variables por defecto; los lotes evitan "too many SQL variables".
 */
object SqlBatch {
    const val SIZE = 500
}