package com.kvelidelivery.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch

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
    onReorder: (String, Int) -> Unit,
    onUpdatePerson: (String, String?, String, String?, String?, String) -> Unit,
    onFinish: () -> Unit
) {
    var expandedDrivers by remember { mutableStateOf(setOf(selectedDriver.number)) }
    var movePerson by remember { mutableStateOf<Person?>(null) }
    var reorderPersonId by remember { mutableStateOf<String?>(null) }
    var editPerson by remember { mutableStateOf<Person?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Зажми человека: порядок в районе и редактирование",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
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
                            reorderPersonId = reorderPersonId,
                            onToggleExpand = {
                                expandedDrivers = if (expandedDrivers.contains(section.driver.number)) {
                                    expandedDrivers - section.driver.number
                                } else {
                                    expandedDrivers + section.driver.number
                                }
                            },
                            onToggleDelivered = onToggleDelivered,
                            onMoveRequest = { movePerson = it },
                            onLongPress = { id ->
                                reorderPersonId = if (reorderPersonId == id) null else id
                            },
                            onReorder = onReorder,
                            onClearReorder = { reorderPersonId = null },
                            onEditRequest = { editPerson = it }
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
                        isReorderTarget = reorderPersonId == person.id,
                        onToggle = { onToggleDelivered(person.id) },
                        onMove = { movePerson = person },
                        onLongPress = {
                            reorderPersonId = if (reorderPersonId == person.id) null else person.id
                        },
                        onMoveUp = { onReorder(person.id, -1) },
                        onMoveDown = { onReorder(person.id, 1) },
                        onEdit = { editPerson = person }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    movePerson?.let { person ->
        ModalBottomSheet(
            onDismissRequest = { movePerson = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            MoveBottomSheetContent(
                person = person,
                timeSlots = timeSlots,
                onMoveToTimeSlot = {
                    onMoveToTimeSlot(person.id, it)
                    movePerson = null
                },
                onMoveToDriver = {
                    onMoveToDriver(person.id, it)
                    movePerson = null
                },
                onMoveToDistrict = {
                    onMoveToDistrict(person.id, it)
                    movePerson = null
                },
                onClose = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        movePerson = null
                    }
                }
            )
        }
    }

    editPerson?.let { person ->
        EditPersonDialog(
            person = person,
            timeSlots = timeSlots,
            onDismiss = { editPerson = null },
            onSave = { name, address, district, role, timeGroup ->
                onUpdatePerson(person.id, name, address, district, role, timeGroup)
                editPerson = null
                reorderPersonId = null
            }
        )
    }
}

@Composable
private fun DriverExpandableCard(
    section: com.kvelidelivery.app.data.DriverSection,
    isExpanded: Boolean,
    isSelected: Boolean,
    reorderPersonId: String?,
    onToggleExpand: () -> Unit,
    onToggleDelivered: (String) -> Unit,
    onMoveRequest: (Person) -> Unit,
    onLongPress: (String) -> Unit,
    onReorder: (String, Int) -> Unit,
    onClearReorder: () -> Unit,
    onEditRequest: (Person) -> Unit
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
            Surface(onClick = onToggleExpand, color = Color.Transparent) {
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
                                isReorderTarget = reorderPersonId == person.id,
                                onToggle = { onToggleDelivered(person.id) },
                                onMove = { onMoveRequest(person) },
                                onLongPress = { onLongPress(person.id) },
                                onMoveUp = { onReorder(person.id, -1) },
                                onMoveDown = { onReorder(person.id, 1) },
                                onEdit = { onEditRequest(person) }
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
    isReorderTarget: Boolean,
    onToggle: () -> Unit,
    onMove: () -> Unit,
    onLongPress: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit
) {
    val alpha = if (person.isDelivered) 0.5f else 1f
    val textDecoration = if (person.isDelivered) TextDecoration.LineThrough else TextDecoration.None

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(
                if (isReorderTarget) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, PrimaryBlue, RoundedCornerShape(12.dp))
                        .background(PrimaryBlue.copy(alpha = 0.08f))
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .pointerInput(person.id) {
                    detectTapGestures(
                        onLongPress = { onLongPress() }
                    )
                }
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

        AnimatedVisibility(visible = isReorderTarget) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Порядок:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = onMoveUp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Выше", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Выше")
                    }
                    FilledTonalButton(
                        onClick = onMoveDown,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Ниже", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ниже")
                    }
                }
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Редактировать", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Редактировать")
                }
            }
        }
    }
}

@Composable
private fun MoveBottomSheetContent(
    person: Person,
    timeSlots: List<String>,
    onMoveToTimeSlot: (String) -> Unit,
    onMoveToDriver: (Driver) -> Unit,
    onMoveToDistrict: (String) -> Unit,
    onClose: () -> Unit
) {
    val districts = listOf(
        "СЗР", "ЮЗР", "БОГДАН", "ЦЕНТР", "НОВЫЙ", "НЧК", "НЮР", "КУГЕСИ"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Переместить",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "${person.name ?: ""} ${person.address}".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        person.district ?: "НЕОПР",
                        person.role,
                        person.timeGroup.takeIf { it.isNotBlank() }?.let { "до $it" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionHeader(icon = { Icon(Icons.Rounded.Schedule, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp)) }, title = "Слот (вышел раньше)")
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            timeSlots.forEach { slot ->
                val selected = slot == person.timeGroup
                FilterChip(
                    selected = selected,
                    onClick = { if (!selected) onMoveToTimeSlot(slot) },
                    label = {
                        Text(
                            "до $slot",
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionHeader(icon = { Icon(Icons.Rounded.DirectionsCar, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp)) }, title = "Водитель")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Driver.entries.forEach { drv ->
                Surface(
                    onClick = { onMoveToDriver(drv) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${drv.number}",
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(drv.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                drv.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionHeader(icon = { Icon(Icons.Rounded.Place, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp)) }, title = "Район")
        Spacer(Modifier.height(10.dp))
        districts.chunked(4).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                row.forEach { d ->
                    val selected = d == person.district
                    FilterChip(
                        selected = selected,
                        onClick = { if (!selected) onMoveToDistrict(d) },
                        label = {
                            Text(
                                d,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: @Composable () -> Unit, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EditPersonDialog(
    person: Person,
    timeSlots: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String?, address: String, district: String?, role: String?, timeGroup: String) -> Unit
) {
    var name by remember { mutableStateOf(person.name ?: "") }
    var address by remember { mutableStateOf(person.address) }
    var district by remember { mutableStateOf(person.district ?: "") }
    var role by remember { mutableStateOf(person.role ?: "") }
    var timeGroup by remember { mutableStateOf(person.timeGroup) }

    val districts = listOf(
        "СЗР", "ЮЗР", "БОГДАНКА", "ЦЕНТР", "НОВЫЙ", "НЧК", "НЮР", "КУГЕСИ"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Редактировать",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адрес") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Роль (клин, офф, бар…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Район",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                districts.chunked(4).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { d ->
                            FilterChip(
                                selected = district == d,
                                onClick = { district = d },
                                label = {
                                    Text(
                                        d,
                                        fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        repeat(4 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    "Слот",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    timeSlots.forEach { slot ->
                        FilterChip(
                            selected = timeGroup == slot,
                            onClick = { timeGroup = slot },
                            label = {
                                Text(
                                    slot,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (address.isNotBlank()) {
                        onSave(
                            name.ifBlank { null },
                            address.trim(),
                            district.ifBlank { null },
                            role.ifBlank { null },
                            timeGroup
                        )
                    }
                },
                enabled = address.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
