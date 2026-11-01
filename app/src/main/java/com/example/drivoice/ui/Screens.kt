package com.example.drivoice.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.drivoice.Conditions
import com.example.drivoice.Drive
import com.example.drivoice.MileageRule
import com.example.drivoice.R
import com.example.drivoice.Store
import com.example.drivoice.Sun
import com.example.drivoice.Turn
import com.example.drivoice.TurnRecord
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal val wide = 1100.dp

internal fun Int.grouped(): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(this)

@Composable
internal fun shortDate(date: LocalDate) = stringResource(R.string.date_short, date.monthValue, date.dayOfMonth)

@Composable
fun DrivoiceApp(driving: Boolean, onAgree: () -> Unit, onLater: () -> Unit) {
    val palette = LocalPalette.current
    val page = when {
        !Store.settings.consented -> Page.CONSENT
        driving -> Page.DRIVING
        else -> Page.PARKED
    }
    AnimatedContent(
        targetState = page,
        modifier = Modifier.fillMaxSize().background(palette.background),
        transitionSpec = {
            if (targetState == Page.DRIVING) EnterTransition.None togetherWith ExitTransition.None
            else fadeIn(tween(Motion.MEDIUM2, easing = Motion.easing)) togetherWith fadeOut(tween(Motion.SHORT1))
        },
        label = "page",
    ) {
        when (it) {
            Page.CONSENT -> ConsentScreen(onAgree, onLater)
            Page.DRIVING -> DrivingScreen()
            Page.PARKED -> ParkedScreen()
        }
    }
}

private enum class Page { CONSENT, DRIVING, PARKED }

@Composable
private fun ConsentScreen(onAgree: () -> Unit, onLater: () -> Unit) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.xxl), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 1040.dp)) {
            Text(stringResource(R.string.consent_title), Type.headline, palette.text, Modifier.enter(0))
            Spacer(Modifier.height(Space.l))
            listOf(R.string.consent_line_1, R.string.consent_line_2, R.string.consent_line_3).forEachIndexed { index, line ->
                Row(Modifier.padding(vertical = Space.xs).enter(index + 1)) {
                    Text("${index + 1}", Type.bodyStrong, palette.accent, Modifier.width(48.dp))
                    Text(stringResource(line), Type.body, palette.text)
                }
            }
            Spacer(Modifier.height(Space.xl))
            SectionHeader(stringResource(R.string.consent_data_title), null)
            listOf(
                R.string.consent_vehicle to R.string.consent_vehicle_detail,
                R.string.consent_location to R.string.consent_location_detail,
                R.string.consent_mic to R.string.consent_mic_detail,
            ).forEachIndexed { index, (title, detail) ->
                Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).enter(index + 4), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), Type.bodyStrong, palette.text, Modifier.width(200.dp))
                    Text(stringResource(detail), Type.body, palette.textSub, Modifier.weight(1f))
                }
                Hairline()
            }
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.enter(7)) {
                ActionButton(stringResource(R.string.consent_agree), onAgree, primary = true)
                ActionButton(stringResource(R.string.consent_later), onLater)
            }
        }
    }
}

@Composable
private fun DrivingScreen() {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(Space.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.driving_title), Type.headline, palette.text, align = TextAlign.Center)
        Spacer(Modifier.height(Space.m))
        Text(stringResource(R.string.driving_body), Type.body, palette.textSub, align = TextAlign.Center)
    }
}

@Composable
private fun ParkedScreen() {
    val palette = LocalPalette.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_today, R.string.tab_before, R.string.tab_mileage, R.string.tab_uses, R.string.tab_family, R.string.tab_settings)
    Column(Modifier.fillMaxSize().padding(horizontal = Space.xxl, vertical = Space.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.product_name), Type.title, palette.text, Modifier.weight(1f))
            Segments(tabs.map { stringResource(it) }, tab, { tab = it })
        }
        Spacer(Modifier.height(Space.xl))
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(Motion.MEDIUM1, Motion.SHORT1, Motion.easing)) + slideInVertically(tween(Motion.LONG1, easing = Motion.easing)) { it / 24 }) togetherWith
                    fadeOut(tween(Motion.SHORT1))
            },
            label = "tab",
        ) { page ->
            when (page) {
                0 -> TodayPage()
                1 -> BeforePage()
                2 -> MileagePage()
                3 -> UsesPage()
                4 -> FamilyPage()
                else -> SettingsPage()
            }
        }
    }
}

@Composable
internal fun TwoColumns(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    AdaptiveColumns(
        wideFrom = wide,
        wide = {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.42f).verticalScroll(rememberScrollState()), content = left)
                Spacer(Modifier.width(Space.xxl))
                Column(Modifier.weight(0.58f).verticalScroll(rememberScrollState()), content = right)
            }
        },
        narrow = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                left()
                Spacer(Modifier.height(Space.xl))
                right()
            }
        },
    )
}

@Composable
private fun TodayPage() {
    val today = LocalDate.now()
    val trips = Store.trips.filter { it.endedAt.toLocalDate() == today }
    val turns = trips.flatMap { it.turns } + Drive.liveTurns
    TwoColumns(
        left = { TodaySummary(today, trips.any { it.nightM > 0 }, trips.any { it.highwayM > 0 }, trips.any { it.overLimit }) },
        right = { TurnLog(turns) },
    )
}

@Composable
private fun TodaySummary(today: LocalDate, night: Boolean, highway: Boolean, overLimit: Boolean) {
    val palette = LocalPalette.current
    val conditions = Store.conditions
    Text(stringResource(R.string.today_earned_label, shortDate(today)), Type.body, palette.textSub)
    Row(verticalAlignment = Alignment.Bottom) {
        RollingNumber(Store.earnedOn(today), Type.hero, palette.text, prefix = "+", fromZero = true)
        Spacer(Modifier.width(Space.s))
        Text(stringResource(R.string.unit_mile), Type.title, palette.text, Modifier.padding(bottom = 18.dp))
    }
    Spacer(Modifier.height(Space.xs))
    Text(stringResource(R.string.today_balance, Store.balance.grouped()), Type.body, palette.textSub)
    Drive.summary?.let { summary ->
        Spacer(Modifier.height(Space.l))
        Column(Modifier.fillMaxWidth().enter(0).background(palette.surface, RoundedCornerShape(24.dp)).padding(Space.m)) {
            Text(
                if (summary.turns == 0) stringResource(R.string.summary_no_turns) else stringResource(R.string.summary_turns, summary.turns, summary.onTime),
                Type.bodyStrong,
                palette.text,
            )
            summary.missed?.let {
                Text(stringResource(R.string.summary_tip, stringResource(if (it == Turn.LEFT) R.string.turn_left else R.string.turn_right)), Type.body, palette.textSub)
            }
        }
    }
    Spacer(Modifier.height(Space.xl))
    SectionHeader(stringResource(R.string.kept_title), null)
    val rows = listOf(
        Triple(stringResource(R.string.condition_night), conditions.noNight, night),
        Triple(stringResource(R.string.condition_highway), conditions.avoidHighway, highway),
        Triple(stringResource(R.string.condition_rest, conditions.restMinutes), conditions.rest, overLimit),
    )
    rows.forEachIndexed { index, (label, active, broken) ->
        Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).enter(index + 1), verticalAlignment = Alignment.CenterVertically) {
            Mark(active && !broken)
            Spacer(Modifier.width(Space.m))
            Text(label, Type.body, if (active) palette.text else palette.textSub, Modifier.weight(1f))
            val status = when {
                !active -> R.string.condition_unused
                broken -> R.string.condition_broken
                else -> R.string.condition_kept
            }
            Text(stringResource(status), Type.body, if (status == R.string.condition_kept) palette.accent else palette.textSub)
        }
        if (index < rows.lastIndex) Hairline()
    }
}

@Composable
private fun TurnLog(records: List<TurnRecord>) {
    val palette = LocalPalette.current
    SectionHeader(stringResource(R.string.turns_title), if (records.isEmpty()) null else stringResource(R.string.turns_summary, records.size, records.count { it.onTime }))
    if (records.isEmpty()) {
        Text(stringResource(R.string.turns_empty), Type.body, palette.textSub, Modifier.padding(vertical = Space.m))
    }
    records.asReversed().forEachIndexed { index, record ->
        Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).enter(index + 3), verticalAlignment = Alignment.CenterVertically) {
            Text(record.time.format(clock), Type.body, palette.textSub, Modifier.width(112.dp))
            Text(stringResource(if (record.turn == Turn.LEFT) R.string.turn_left else R.string.turn_right), Type.body, palette.text, Modifier.width(132.dp))
            Text(stringResource(if (record.onTime) R.string.turn_on_time else R.string.turn_late), Type.body, palette.textSub, Modifier.width(150.dp))
            Text(if (record.guided) "" else stringResource(R.string.turn_unguided), Type.label, palette.textSub, Modifier.weight(1f))
            val amount = when {
                !record.onTime -> 0
                record.guided -> MileageRule.TURN_ON_TIME
                else -> MileageRule.TURN_UNGUIDED
            }
            Text(
                if (amount > 0) stringResource(R.string.amount_plus, amount.toString()) else amount.toString(),
                Type.bodyStrong,
                if (amount > 0) palette.accent else palette.textSub,
                align = TextAlign.End,
            )
        }
        Hairline()
    }
}

@Composable
internal fun SectionHeader(title: String, trailing: String?) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(bottom = Space.xs), verticalAlignment = Alignment.Bottom) {
        Text(title, Type.titleSmall, palette.text, Modifier.weight(1f))
        if (trailing != null) Text(trailing, Type.body, palette.textSub)
    }
    Hairline()
}

@Composable
private fun BeforePage() {
    TwoColumns(
        left = { ConditionsEditor(Store.conditions, Store::update) },
        right = { RouteStatus() },
    )
}

@Composable
private fun ConditionsEditor(conditions: Conditions, onChange: (Conditions) -> Unit) {
    val palette = LocalPalette.current
    SectionHeader(stringResource(R.string.conditions_title), null)
    SwitchRow(0, stringResource(R.string.condition_night), stringResource(R.string.conditions_night_detail), conditions.noNight) {
        onChange(conditions.copy(noNight = it))
    }
    Hairline()
    SwitchRow(1, stringResource(R.string.condition_highway), stringResource(R.string.conditions_highway_detail), conditions.avoidHighway) {
        onChange(conditions.copy(avoidHighway = it))
    }
    Hairline()
    SwitchRow(2, stringResource(R.string.conditions_rest_title), stringResource(R.string.conditions_rest_detail), conditions.rest) {
        onChange(conditions.copy(rest = it))
    }
    Row(Modifier.fillMaxWidth().padding(start = Space.m, bottom = Space.m).enter(3), verticalAlignment = Alignment.CenterVertically) {
        val enabled = conditions.rest
        Text(stringResource(R.string.conditions_rest_limit), Type.body, palette.textSub, Modifier.weight(1f))
        StepButton("−", stringResource(R.string.decrease), enabled) {
            onChange(conditions.copy(restMinutes = (conditions.restMinutes - MileageRule.REST_STEP).coerceAtLeast(MileageRule.REST_MIN)))
        }
        Box(Modifier.width(180.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(conditions.restMinutes, Type.title, if (enabled) palette.text else palette.textSub)
                Text(stringResource(R.string.unit_minute), Type.body, palette.textSub, Modifier.padding(start = 4.dp, bottom = 3.dp))
            }
        }
        StepButton("+", stringResource(R.string.increase), enabled) {
            onChange(conditions.copy(restMinutes = (conditions.restMinutes + MileageRule.REST_STEP).coerceAtMost(MileageRule.REST_MAX)))
        }
    }
    Hairline()
    Spacer(Modifier.height(Space.l))
    Text(stringResource(R.string.conditions_family_note), Type.body, palette.textSub, Modifier.enter(4))
}

@Composable
private fun RouteStatus() {
    val palette = LocalPalette.current
    val route = Drive.route
    val conditions = Store.conditions
    val sunset = Sun.sunset(LocalDate.now(), Sun.SEOUL_LAT, Sun.SEOUL_LON, ZoneId.systemDefault())
    val dash = stringResource(R.string.dash)
    SectionHeader(stringResource(R.string.route_title), null)
    val rows = listOf(
        stringResource(R.string.route_destination) to (route.destination ?: stringResource(R.string.route_none)),
        stringResource(R.string.route_option) to when {
            !conditions.avoidHighway || route.destination == null -> dash
            route.freeRoad == true -> stringResource(R.string.route_free)
            route.freeRoad == false -> stringResource(R.string.route_free_failed)
            route.freeRequested -> stringResource(R.string.route_free_requested)
            else -> dash
        },
        stringResource(R.string.route_arrival) to (route.arrival?.format(clock) ?: dash),
        stringResource(R.string.route_sunset) to (sunset?.format(clock) ?: dash),
        stringResource(R.string.route_continuous) to stringResource(R.string.route_continuous_value, (Drive.continuousMs / 60_000).toInt(), conditions.restMinutes),
    )
    rows.forEachIndexed { index, (label, value) ->
        Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).enter(index), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Type.body, palette.textSub, Modifier.weight(1f))
            Text(value, Type.bodyStrong, palette.text, align = TextAlign.End)
        }
        Hairline()
    }
    if (conditions.noNight && route.arrivesAfterSunset) {
        Spacer(Modifier.height(Space.m))
        Text(stringResource(R.string.route_after_sunset), Type.body, palette.text, Modifier.enter(rows.size))
    }
}

@Composable
internal fun SwitchRow(index: Int, title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Pressable(
        onClick = { onChange(!checked) },
        modifier = Modifier.fillMaxWidth().enter(index).semantics { toggleableState = ToggleableState(checked) },
        shape = RoundedCornerShape(20.dp),
        role = Role.Switch,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, Type.bodyStrong, palette.text)
                Text(detail, Type.label, palette.textSub)
            }
            Toggle(checked)
        }
    }
}
