package io.github.mr3zee.rwizard.domain.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class UUID(val value: String) {
    
    override fun toString(): String = value
    
    companion object {
        fun v4(): UUID {
            val chars = "0123456789abcdef"
            val random = Random.Default
            
            val uuid = buildString(36) {
                repeat(32) {
                    append(chars[random.nextInt(16)])
                    if (length == 8 || length == 13 || length == 18 || length == 23) {
                        append('-')
                    }
                }
            }
            
            return UUID(uuid)
        }
        
        fun fromString(value: String): UUID {
            require(isValid(value)) { "Invalid UUID format: $value" }
            return UUID(value)
        }
        
        private fun isValid(value: String): Boolean {
            return value.matches(
                Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
            )
        }
    }
}
