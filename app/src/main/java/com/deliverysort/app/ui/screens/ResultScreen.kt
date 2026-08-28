package com.kvelidelivery.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kvelidelivery.app.data.Driver
import com.kvelidelivery.app.data.Person
import com.kvelidelivery.app.data.TimeSlotGroup
import com.kvelidelivery.app.ui.theme.AccentGreen
import com.kvelidelivery.app.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    selectedDriver: Driver,
    todayDate: String,
    timeSlots: List<String>,
    structured: List<TimeSlotGroup>,
    undefinedPeople: List<Person>,
    onToggleDelivered: (String) -> Unit,
    onMoveToDriver: (String, Driver) -> Unit,
    onMoveToDistrict: (String, String) -> Unit,
    onMoveToTimeSlot: (String, String) -> Unit,
    onFinish: () -> Unit
) {
    var expandedDrivers by remember { mutableStateOf(setOf(selectedDriver.number)) }
    var showMoveDialog by remember { mutableStateOf<Person?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Список развоза",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            todayDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Icon(Icons.Rounded.Flag, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Завершить поездку",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Ты: ${selectedDriver.title}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Слоты: ${timeSlots.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            structured.forEach { slotGroup ->
                item {
                    Text(
                        "--- СПИСОК ДО ${slotGroup.time} ---",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }

                slotGroup.drivers.forEach { section ->
                    item {
                        DriverExpandableCard(
                            section = section,
                            isExpanded = expandedDrivers.contains(section.driver.number),
                            isSelected = section.driver == selectedDriver,
                            onToggleExpand = {
                                expandedDrivers = if (expandedDrivers.contains(section.driver.number)) {
                                    expandedDrivers - section.driver.number
                                } else {
                                    expandedDrivers + section.driver.number
                                }
                            },
                            onToggleDelivered = onToggleDelivered,
                            onMoveRequest = { showMoveDialog = it }
                        )
                    }
                }
            }

            if (undefinedPeople.isNotEmpty()) {
                item {
                    Text(
                        "--- НЕОПРЕДЕЛЕНО ---",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
                items(undefinedPeople, key = { it.id }) { person ->
                    PersonRow(
                        person = person,
                        onToggle = { onToggleDelivered(person.id) },
                        onMove = { showMoveDialog = person }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    showMoveDialog?.let { person ->
        MovePersonDialog(
            person = person,
            timeSlots = timeSlots,
            onDismiss = { showMoveDialog = null },
            onMoveToDriver = { drv ->
                onMoveToDriver(person.id, drv)
                showMoveDialog = null
            },
            onMoveToDistrict = { dist ->
                onMoveToDistrict(person.id, dist)
                showMoveDialog = null
            },
            onMoveToTimeSlot = { slot ->
                onMoveToTimeSlot(person.id, slot)
                showMoveDialog = null
            }
        )
    }
}

@Composable
private fun DriverExpandableCard(
    section: com.kvelidelivery.app.data.DriverSection,
    isExpanded: Boolean,
    isSelected: Boolean,
    onToggleExpand: () -> Unit,
    onToggleDelivered: (String) -> Unit,
    onMoveRequest: (Person) -> Unit
) {
    val borderColor = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                PrimaryBlue.copy(alpha = 0.06f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
    ) {
        Column {
            // Header
            Surface(
                onClick = onToggleExpand,
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        section.driver.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PrimaryBlue.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "${section.totalCount}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
                    section.districts.forEach { distGroup ->
                        Text(
                            "-- (${distGroup.activeCount}) ${distGroup.district} --",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        distGroup.people.forEach { person ->
                            PersonRow(
                                person = person,
                                onToggle = { onToggleDelivered(person.id) },
                                onMove = { onMoveRequest(person) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(
    person: Person,
    onToggle: () -> Unit,
    onMove: () -> Unit
) {
    val alpha = if (person.isDelivered) 0.5f else 1f
    val textDecoration = if (person.isDelivered) TextDecoration.LineThrough else TextDecoration.None

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = person.isDelivered,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = AccentGreen)
        )
        Column(modifier = Modifier.weight(1f)) {
            val namePart = if (person.name != null) "${person.name} - " else ""
            Text(
                text = "$namePart${person.address}",
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = textDecoration,
                fontWeight = if (person.isDelivered) FontWeight.Normal else FontWeight.Medium
            )
            Row {
                Text(
                    "(${person.district ?: "НЕОПР"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = textDecoration
                )
                if (person.role != null) {
                    Text(
                        ", ${person.role}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = textDecoration
                    )
                }
                if (person.timeGroup.isNotBlank()) {
                    Text(
                        " · до ${person.timeGroup}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = textDecoration
                    )
                }
            }
        }
        IconButton(onClick = onMove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.SwapHoriz,
                contentDescription = "Переместить",
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MovePersonDialog(
    person: Person,
    timeSlots: List<String>,
    onDismiss: () -> Unit,
    onMoveToDriver: (Driver) -> Unit,
    onMoveToDistrict: (String) -> Unit,
    onMoveToTimeSlot: (String) -> Unit
) {
    val districts = listOf(
        "СЗР", "ЮЗР", "БОГДАНКА", "ЦЕНТР", "НОВЫЙ", "НЧК", "НЮР", "КУГЕСИ"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переместить") },
        text = {
            Column {
                Text(
                    "${person.name ?: ""} ${person.address}".trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (person.timeGroup.isNotBlank()) {
                    Text(
                        "Сейчас: до ${person.timeGroup}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("В другой слот (вышел раньше):", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    timeSlots.forEach { slot ->
                        val selected = slot == person.timeGroup
                        if (selected) {
                            Button(
                                onClick = { },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text(slot, fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onMoveToTimeSlot(slot) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text(slot, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("К водителю:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Driver.entries.forEach { drv ->
                    TextButton(
                        onClick = { onMoveToDriver(drv) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(drv.title, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Или в район:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                districts.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { d ->
                            OutlinedButton(
                                onClick = { onMoveToDistrict(d) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(d, fontSize = 12.sp)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
