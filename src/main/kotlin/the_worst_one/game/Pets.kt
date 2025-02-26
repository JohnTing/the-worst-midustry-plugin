package the_worst_one.game

import arc.graphics.Color
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.util.Time
import the_worst_one.cfg.Globals
import the_worst_one.cfg.Reloadable
import com.beust.klaxon.Json
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import mindustry.content.Fx
import the_worst_one.game.u.User
import mindustry.entities.Effect
import mindustry.entities.Predict
import mindustry.entities.Units
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Sounds.shoot
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Logger
import java.io.File
import java.util.*

class Pets(val users: Users, val logger: Logger, override var configPath: String): HashMap<String, Pets.Stats>(), Reloadable {
    private val dif = Vec2()
    private val vel = Vec2()
    private val playerPos = Vec2()

    init {
        users.quests.pets = this
        logger.run(EventType.Trigger.update) {
            for ((_, u) in users) update(u)
        }
    }

    fun update(user: User) {
        for(p in user.pets) {
            for (o in user.pets) {
                if(p == o) continue
                p.vel.add(dif.set(o.pos).sub(p.pos).scl(p.stats.mating * Time.delta))
            }

            val velLen = p.vel.len()

            playerPos.set(user.inner.x, user.inner.y)
            dif.set(playerPos).sub(p.pos)

            p.vel.add(dif.setLength(p.stats.attachment * Time.delta))
            p.vel.scl((velLen + p.stats.acceleration * Time.delta)/velLen)

            if(p.stats.maxSpeed != p.stats.minSpeed) {
                p.vel.scl(Mathf.clamp(velLen, p.stats.minSpeed, p.stats.maxSpeed)/velLen)
            }

            // there should be some order in this i don't remember
            vel.set(p.vel)
            // we don't want to ever reach zero
            p.vel.sub(vel.scl(Mathf.clamp(p.stats.friction * Time.delta, 0f, 0.9f)))
            vel.set(p.vel)
            p.pos.add(vel.scl(Time.delta))

            Call.effect(p.stats.effect, p.pos.x, p.pos.y, p.vel.angle(), Color(1f, 1f, 1f))

            shoot(user, p)
        }

    }

    companion object {
        val h1 = Vec2()
    }

    private fun shoot(user: User, p: Pet) {
        val weapon = p.stats.wp ?: return
        p.weaponState.reload += Time.delta / 60f
        if (p.weaponState.reload < weapon.stats.reload) {
            return
        }

        val aim = if(user.inner.unit().isShooting) {
            h1.set(user.inner.unit().aimX, user.inner.unit().aimY)
        } else {
            val enemy = Units.closestEnemy(
                user.inner.team(),
                p.pos.x,
                p.pos.y,
                weapon.bullet.range
            ) { true } ?: return

            Predict.intercept(
                p.pos.x,
                p.pos.y,
                enemy.x,
                enemy.y,
                enemy.vel.x - p.vel.x,
                enemy.vel.y - p.vel.y,
                weapon.bullet.speed,
            )
        }

        p.weaponState.reload = 0f

        weapon.shoot(aim, p.pos, p.vel, user.inner.team(), p.weaponState)
    }

    override fun reload() {
        clear()
        try {
            val pets = Klaxon().parse<Map<String, JsonObject>>(File(configPath))!!
            for((n, p) in pets) {
                val s = Klaxon().parseFromJsonObject<Stats>(p)!!
                s.initialize(n)
                put(n, s)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Globals.loadFailMessage("pets", e)
            println(Globals.effectListString())
            Fs.createDefault(configPath, mapOf(
                "somePet" to Stats()
            ))
        }
    }

    fun populate(user: User) {
        for (p in user.data.display.pets) {
            val stats = get(p)
            if (stats != null) {
                user.pets.add(Pet(stats))
            }
        }
    }

    class Pet(val stats: Stats) {
        val pos = Vec2()
        val vel = Vec2(1f, 0f)
        val weaponState = PewPew.State()
    }

    class Stats(
        val acceleration: Float = 100f,
        val maxSpeed: Float = 1000f,
        val minSpeed: Float = 0f,
        val friction: Float = 0f,
        val mating: Float = 50f,
        val attachment: Float = 50f,
        val effectName: String = "fallSmoke",
        val weapon: PewPew.Stats? = PewPew.Stats(),
    ) {
        @Json(ignored = true) lateinit var effect: Effect
        @Json(ignored = true) lateinit var name: String
        @Json(ignored = true) var wp: PewPew.Weapon? = null

        fun initialize(name: String) {
            this.name = name
            effect = Globals.effect(effectName) ?: throw RuntimeException("invalid effect in $name")
            if (weapon != null) {
                try {
                    wp = PewPew.Weapon(weapon)
                } catch (e: Exception) {
                    throw Exception("invalid weapon in $name", e)
                }
            }
            println(effect.id)
        }
    }
}