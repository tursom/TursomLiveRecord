package cn.tursom.record.context

import cn.tursom.core.util.Snowflake
import cn.tursom.core.util.base62
import kotlin.random.Random

class SnowflakeIdContextImpl(
  private val snowflake: () -> Snowflake,
) : IdContext {
  companion object {
    val defaultSnowflake = Snowflake(Random.nextInt())
  }
  constructor(snowflake: Snowflake = defaultSnowflake) : this({ snowflake })

  override fun id() = snowflake().id.base62()
}