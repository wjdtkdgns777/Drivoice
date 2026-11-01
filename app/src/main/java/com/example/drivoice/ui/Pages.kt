package com.example.drivoice.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.drivoice.BuildConfig
import com.example.drivoice.Code
import com.example.drivoice.Drive
import com.example.drivoice.Mileage
import com.example.drivoice.MileageRule
import com.example.drivoice.R
import com.example.drivoice.Store
import com.example.drivoice.Tier
import com.example.drivoice.Tx
import java.time.LocalDate

@Composable
internal fun MileagePage() {
    TwoColumns(left = { Balance() }, right = { Ledger() })
}

@Composable
private fun Balance() {
    val palette = LocalPalette.current
    val still = LocalStill.current
    val today = LocalDate.now()
    val balance = Store.balance
    val tier = Mileage.tier(Store.ledger, Store.trips, today)
    Text(stringResource(R.string.balance_label), Type.body, palette.textSub)
    Row(verticalAlignment = Alignment.Bottom) {
        RollingNumber(balance, Type.hero, palette.text, fromZero = true)
        Spacer(Modifier.width(Space.s))
        Text(stringResource(R.string.unit_mile), Type.title, palette.text, Modifier.padding(bottom = 18.dp))
    }
    Spacer(Modifier.height(Space.xl))
    val (name, next, goal, rule) = when (tier) {
        Tier.GREEN -> listOf(R.string.tier_green, R.string.tier_silver_short, MileageRule.SILVER, R.string.tier_rule_silver)
        Tier.SILVER -> listOf(R.string.tier_silver, R.string.tier_gold_short, MileageRule.GOLD, R.string.tier_rule_gold)
        Tier.GOLD -> listOf(R.string.tier_gold, null, null, R.string.tier_top)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(stringResource(name as Int), Type.titleSmall, palette.text, Modifier.weight(1f))
        if (next != null && goal != null) {
            Text(stringResource(R.string.tier_to_next, stringResource(next as Int), ((goal as Int) - balance).coerceAtLeast(0).grouped()), Type.body, palette.textSub)
        }
    }
    Spacer(Modifier.height(Space.s))
    val target = if (goal == null) 1f else (balance / (goal as Int).toFloat()).coerceIn(0f, 1f)
    val fill = remember { Animatable(if (still) target else 0f) }
    LaunchedEffect(target) { fill.animateTo(target, tween(Motion.LONG2 * 2, Motion.MEDIUM1, Motion.easing)) }
    Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(palette.line)) {
        Box(Modifier.fillMaxWidth(fill.value).fillMaxHeight().clip(CircleShape).background(palette.accent))
    }
    Spacer(Modifier.height(Space.m))
    Text(stringResource(rule as Int), Type.label, palette.textSub)
}

@Composable
private fun Ledger() {
    val palette = LocalPalette.current
    SectionHeader(stringResource(R.string.ledger_title), null)
    val entries = Store.ledger.sortedByDescending { it.at }
    if (entries.isEmpty()) Text(stringResource(R.string.ledger_empty), Type.body, palette.textSub, Modifier.padding(vertical = Space.m))
    var index = 0
    entries.groupBy { it.at.toLocalDate() }.forEach { (date, txs) ->
        Text(shortDate(date), Type.label, palette.textSub, Modifier.padding(top = Space.m, bottom = Space.xs).enter(index++))
        txs.forEach { tx ->
            Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).enter(index++), verticalAlignment = Alignment.CenterVertically) {
                Text(tx.title(), Type.body, palette.text, Modifier.weight(1f))
                Text(stringResource(R.string.amount_plus, tx.miles.grouped()), Type.bodyStrong, palette.text)
            }
            Hairline()
        }
    }
}

@Composable
private fun Tx.title() = when (code) {
    Code.TURN -> stringResource(R.string.ledger_turns, count)
    Code.TURN_UNGUIDED -> stringResource(R.string.ledger_turns_unguided, count)
    Code.DISTANCE -> stringResource(if (miles > count) R.string.ledger_distance_double else R.string.ledger_distance, count)
    Code.REST -> stringResource(R.string.ledger_rest)
    Code.KEPT_DAY -> stringResource(R.string.ledger_kept_day)
    Code.STREAK -> stringResource(R.string.ledger_streak)
    Code.PARTNER -> stringResource(R.string.ledger_partner)
}

@Composable
internal fun UsesPage() {
    val palette = LocalPalette.current
    TwoColumns(
        left = {
            SectionHeader(stringResource(R.string.uses_title), null)
            listOf(
                R.string.use_bluehands to R.string.use_bluehands_detail,
                R.string.use_next_car to R.string.use_next_car_detail,
                R.string.use_bluemembers to R.string.use_bluemembers_detail,
                R.string.use_insurance to R.string.use_insurance_detail,
            ).forEachIndexed { index, (title, detail) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 112.dp).enter(index), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(title), Type.bodyStrong, palette.text)
                        Text(stringResource(detail), Type.label, palette.textSub)
                    }
                    Text(stringResource(R.string.use_status), Type.label, palette.textSub)
                }
                Hairline()
            }
        },
        right = {
            SectionHeader(stringResource(R.string.rest_places_title), null)
            Drive.places.forEachIndexed { index, place ->
                Row(Modifier.fillMaxWidth().heightIn(min = 112.dp).enter(index), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(place.name), Type.bodyStrong, palette.text)
                        Text(stringResource(R.string.km, "%.1f".format(Drive.distanceKm(place))), Type.label, palette.textSub)
                    }
                    if (Drive.pendingPlace == place) {
                        Text(stringResource(R.string.waypoint_added), Type.body, palette.accent)
                    } else {
                        ActionButton(stringResource(R.string.add_waypoint), { Drive.addRestStop(place) })
                    }
                }
                Hairline()
            }
            Spacer(Modifier.height(Space.m))
            Text(stringResource(R.string.rest_places_note), Type.body, palette.textSub, Modifier.enter(Drive.places.size))
        },
    )
}

@Composable
internal fun FamilyPage() {
    val palette = LocalPalette.current
    val settings = Store.settings
    val today = LocalDate.now()
    val weekKept = (0L..6L).all { back -> Store.ledger.any { it.code == Code.KEPT_DAY && it.at.toLocalDate() == today.minusDays(back) } }
    val signalGood = (Mileage.signalRate(Store.trips, today.minusDays(6)) ?: 0.0) >= MileageRule.SAFE_RATE
    TwoColumns(
        left = {
            Text(stringResource(R.string.family_code_title), Type.body, palette.textSub)
            Text(settings.inviteCode.chunked(3).joinToString(" "), Type.hero, palette.text, Modifier.enter(0))
            Text(stringResource(R.string.family_code_note), Type.body, palette.textSub, Modifier.enter(1))
            Spacer(Modifier.height(Space.xl))
            SwitchRow(2, stringResource(R.string.family_share), stringResource(R.string.family_share_detail), settings.familyShare) {
                Store.update(settings.copy(familyShare = it))
            }
            Hairline()
            SwitchRow(3, stringResource(R.string.family_share_conditions), stringResource(R.string.family_share_conditions_detail), settings.shareConditions) {
                Store.update(settings.copy(shareConditions = it))
            }
        },
        right = {
            SectionHeader(stringResource(R.string.family_visible_title), null)
            val tier = when (Mileage.tier(Store.ledger, Store.trips, today)) {
                Tier.GREEN -> R.string.tier_green
                Tier.SILVER -> R.string.tier_silver
                Tier.GOLD -> R.string.tier_gold
            }
            val rows = buildList {
                add(stringResource(R.string.balance_label) to stringResource(R.string.miles_value, Store.balance.grouped()))
                if (settings.familyShare) {
                    add(stringResource(R.string.family_tier) to stringResource(tier))
                    add(stringResource(R.string.badge_week) to stringResource(if (weekKept) R.string.badge_earned else R.string.badge_not_yet))
                    add(stringResource(R.string.badge_signal) to stringResource(if (signalGood) R.string.badge_earned else R.string.badge_not_yet))
                }
            }
            rows.forEachIndexed { index, (label, value) ->
                Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).enter(index), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Type.body, palette.text, Modifier.weight(1f))
                    Text(value, Type.bodyStrong, if (value == stringResource(R.string.badge_earned)) palette.accent else palette.text, align = TextAlign.End)
                }
                Hairline()
            }
            if (!settings.familyShare) {
                Text(stringResource(R.string.family_balance_only), Type.body, palette.textSub, Modifier.padding(top = Space.m))
            }
            Spacer(Modifier.height(Space.xl))
            SectionHeader(stringResource(R.string.family_hidden_title), null)
            Text(stringResource(R.string.family_hidden), Type.body, palette.textSub, Modifier.padding(top = Space.m).enter(rows.size))
        },
    )
}

@Composable
internal fun SettingsPage() {
    val palette = LocalPalette.current
    val settings = Store.settings
    var clearStep by rememberSaveable { mutableStateOf(0) }
    var seeded by rememberSaveable { mutableStateOf(false) }
    TwoColumns(
        left = {
            SectionHeader(stringResource(R.string.settings_title), null)
            SwitchRow(0, stringResource(R.string.settings_sound), stringResource(R.string.settings_sound_detail), settings.sound) {
                Store.update(settings.copy(sound = it))
            }
            Hairline()
            SwitchRow(1, stringResource(R.string.settings_voice), stringResource(R.string.settings_voice_detail), settings.voice) {
                Store.update(settings.copy(voice = it))
            }
            Hairline()
            Spacer(Modifier.height(Space.l))
            Text(stringResource(R.string.settings_privacy), Type.body, palette.textSub, Modifier.enter(2))
            Spacer(Modifier.height(Space.m))
            val label = when (clearStep) {
                0 -> R.string.settings_clear
                1 -> R.string.settings_clear_confirm
                else -> R.string.settings_cleared
            }
            ActionButton(stringResource(label), {
                if (clearStep == 1) {
                    Store.clear()
                    Drive.summary = null
                }
                clearStep = (clearStep + 1).coerceAtMost(2)
            }, Modifier.enter(3))
        },
        right = {
            if (BuildConfig.DEBUG) {
                SectionHeader(stringResource(R.string.demo_title), null)
                DemoRow(0, R.string.demo_replay, R.string.demo_replay_detail) {
                    ActionButton(stringResource(if (Drive.replaying) R.string.stop else R.string.start), { if (Drive.replaying) Drive.stopReplay() else Drive.replay() })
                }
                Hairline()
                SwitchRow(1, stringResource(R.string.demo_fast), stringResource(R.string.demo_fast_detail), Drive.timeScale > 1) {
                    Drive.timeScale = if (it) DEMO_SCALE else 1
                }
                Hairline()
                DemoRow(2, R.string.demo_seed, null) {
                    if (seeded) Text(stringResource(R.string.demo_seed_done), Type.body, palette.accent)
                    else ActionButton(stringResource(R.string.start), { Drive.seedHistory(); seeded = true })
                }
                Hairline()
                DemoRow(3, R.string.demo_state, null) {
                    Text(Drive.debug, Type.label, palette.textSub)
                }
            }
        },
    )
}

@Composable
private fun DemoRow(index: Int, title: Int, detail: Int?, trailing: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = Space.m).enter(index), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), Type.bodyStrong, palette.text)
            if (detail != null) Text(stringResource(detail), Type.label, palette.textSub)
        }
        trailing()
    }
}

private const val DEMO_SCALE = 30
