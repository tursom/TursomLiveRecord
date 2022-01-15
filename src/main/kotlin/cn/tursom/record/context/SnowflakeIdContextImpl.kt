package cn.tursom.record.context

import cn.tursom.core.Snowflake
import cn.tursom.core.base62
import kotlin.random.Random

class SnowflakeIdContextImpl(
  private val snowflake: Snowflake = Snowflake(Random.nextInt()),
) : IdContext {
  override fun id() = snowflake.id.base62()
}