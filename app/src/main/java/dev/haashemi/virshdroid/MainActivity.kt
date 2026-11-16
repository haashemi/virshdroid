@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.haashemi.virshdroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable private data object HomeKey : NavKey
@Serializable private data class EditKey(val id: String?) : NavKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent {
            val context = LocalContext.current
            MaterialExpressiveTheme(
                colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
                motionScheme = MotionScheme.expressive(),
            ) { App(store) }
        }
    }
}

@Composable
private fun App(store: Store) {
    val saved = store.saved.collectAsState(initial = null).value ?: return
    val backStack = rememberNavBackStack(HomeKey)
    val scope = rememberCoroutineScope()
    val back: () -> Unit = { backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    saved = saved,
                    onSelect = { scope.launch { store.select(it.id) } },
                    onEdit = { backStack.add(EditKey(it?.id)) },
                    onTrust = { scope.launch { store.save(it) } },
                )
            }
            entry<EditKey> { key ->
                EditScreen(
                    initial = saved.servers.find { it.id == key.id },
                    onSave = { scope.launch { store.save(it) }; back() },
                    onDelete = { id -> scope.launch { store.remove(id) }; back() },
                    onBack = back,
                )
            }
        },
    )
}

/** Picks the server to connect to; [onEdit] with null adds a new one. */
@Composable
private fun ServerMenu(saved: Saved, onSelect: (Server) -> Unit, onEdit: (Server?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(saved.selected?.title ?: "Virshdroid", style = MaterialTheme.typography.titleLarge)
            Icon(Icons.Default.ArrowDropDown, "Choose server")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            saved.servers.forEach { server ->
                DropdownMenuItem(
                    text = { Text(server.title) },
                    onClick = { open = false; onSelect(server) },
                    leadingIcon = { if (server.id == saved.selected?.id) Icon(Icons.Default.Check, "Selected") },
                )
            }
            if (saved.servers.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Add server") },
                onClick = { open = false; onEdit(null) },
                leadingIcon = { Icon(Icons.Default.Add, null) },
            )
        }
    }
}

@Composable
private fun EditScreen(initial: Server?, onSave: (Server) -> Unit, onDelete: (String) -> Unit, onBack: () -> Unit) {
    var s by remember { mutableStateOf(initial ?: Server()) }
    var port by remember { mutableStateOf(s.port.toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val importKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { s = s.copy(privateKey = context.contentResolver.openInputStream(it)!!.use { it.readBytes().decodeToString() }) }
    }
    val portNumber = port.toIntOrNull()?.takeIf { it in 1..65535 }
    val valid = s.host.isNotBlank() && s.user.isNotBlank() && portNumber != null &&
        (if (s.useKey) s.privateKey.isNotBlank() else s.password.isNotEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Add server" else "Edit server") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (initial != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(s.name, { s = s.copy(name = it) }, "Name")
            Field(s.host, { s = s.copy(host = it.trim()) }, "Host", KeyboardType.Uri)
            Field(port, { port = it }, "Port", KeyboardType.Number)
            Field(s.user, { s = s.copy(user = it.trim()) }, "Username")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Password", "Private key").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = s.useKey == (i == 1),
                        onClick = { s = s.copy(useKey = i == 1) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            if (s.useKey) {
                Field(s.privateKey, { s = s.copy(privateKey = it) }, "Private key (OpenSSH / PEM)", singleLine = false)
                OutlinedButton(onClick = { importKey.launch(arrayOf("*/*")) }, shapes = ButtonDefaults.shapes()) {
                    Text("Import key file")
                }
                Field(s.passphrase, { s = s.copy(passphrase = it) }, "Key passphrase (optional)", KeyboardType.Password)
            } else {
                Field(s.password, { s = s.copy(password = it) }, "Password", KeyboardType.Password)
            }
            Button(
                onClick = {
                    val moved = initial != null && (initial.host != s.host || initial.port != portNumber)
                    onSave(
                        s.copy(
                            port = portNumber!!,
                            hostKey = s.hostKey.takeUnless { moved }, // a different host must be trusted again
                            password = if (s.useKey) "" else s.password,
                            privateKey = if (s.useKey) s.privateKey else "",
                            passphrase = if (s.useKey) s.passphrase else "",
                        )
                    )
                },
                enabled = valid,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${s.title}?") },
            text = { Text("The saved connection and its credentials will be removed from this device.") },
            confirmButton = { TextButton(onClick = { onDelete(s.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HomeScreen(saved: Saved, onSelect: (Server) -> Unit, onEdit: (Server?) -> Unit, onTrust: (Server) -> Unit) {
    val server = saved.selected
    var vms by remember { mutableStateOf<List<Vm>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Pair<String, String>?>(null) }
    var hostKey by remember { mutableStateOf<HostKeyException?>(null) }
    var destroy by remember { mutableStateOf<Vm?>(null) }
    val scope = rememberCoroutineScope()

    /** Runs [action] (if any), then reloads the list; failures are shown in a dialog titled [failure]. */
    suspend fun load(failure: String = "Couldn't load virtual machines", action: suspend () -> Unit = {}) {
        val target = server ?: return
        busy = true
        try {
            action()
            vms = Virsh.list(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HostKeyException) {
            hostKey = e
        } catch (e: Exception) {
            error = failure to (e.message ?: e.toString())
        } finally {
            busy = false
        }
    }

    fun act(action: String, verb: String, vm: Vm) {
        val target = server ?: return
        scope.launch { load("Couldn't $verb ${vm.name}") { Virsh.run(target, action, vm.name) } }
    }

    val context = LocalContext.current
    val requestLocalNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { load() }
    }
    // Connects automatically whenever the selected server changes.
    LaunchedEffect(server) {
        vms = null
        if (server == null) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 37 &&
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocalNetwork.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            load()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ServerMenu(saved, onSelect, onEdit) },
                actions = {
                    if (server != null) {
                        IconButton(onClick = { onEdit(server) }) { Icon(Icons.Default.Edit, "Edit ${server.title}") }
                        IconButton(onClick = { scope.launch { load() } }, enabled = !busy) { Icon(Icons.Default.Refresh, "Refresh") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val list = vms
            when {
                server == null -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("No servers yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { onEdit(null) }, shapes = ButtonDefaults.shapes()) { Text("Add server") }
                }
                list == null && busy -> LoadingIndicator(Modifier.align(Alignment.Center))
                list == null -> Placeholder("Not connected")
                list.isEmpty() -> Placeholder("No virtual machines")
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.name }) { vm ->
                        VmCard(
                            vm = vm,
                            enabled = !busy,
                            onStart = { act("start", "start", vm) },
                            onShutdown = { act("shutdown", "shut down", vm) },
                            onDestroy = { destroy = vm },
                        )
                    }
                }
            }
            if (busy && vms != null) LinearWavyProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    if (server != null) hostKey?.let { key ->
        AlertDialog(
            onDismissRequest = { hostKey = null },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text(if (key.changed) "Host key changed" else "Trust this host?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (key.changed) "The key offered by ${server.host} differs from the one you accepted before. " +
                            "Someone may be intercepting the connection."
                        else "First connection to ${server.host}. Check that this fingerprint matches the server's."
                    )
                    Text("${key.type}\n${key.fingerprint}", fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = { hostKey = null; onTrust(server.copy(hostKey = key.key)) }) {
                    Text(if (key.changed) "Replace key" else "Trust")
                }
            },
            dismissButton = { TextButton(onClick = { hostKey = null }) { Text("Cancel") } },
        )
    }

    destroy?.let { vm ->
        AlertDialog(
            onDismissRequest = { destroy = null },
            title = { Text("Destroy ${vm.name}?") },
            text = { Text("This immediately powers off the virtual machine, like pulling the plug. Unsaved data in it may be lost.") },
            confirmButton = { TextButton(onClick = { destroy = null; act("destroy", "destroy", vm) }) { Text("Destroy") } },
            dismissButton = { TextButton(onClick = { destroy = null }) { Text("Cancel") } },
        )
    }

    error?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun VmCard(vm: Vm, enabled: Boolean, onStart: () -> Unit, onShutdown: () -> Unit, onDestroy: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(vm.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = if (vm.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(vm.state, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = enabled && !vm.running, shapes = ButtonDefaults.shapes()) { Text("Start") }
                FilledTonalButton(onClick = onShutdown, enabled = enabled && vm.running, shapes = ButtonDefaults.shapes()) {
                    Text("Shut down")
                }
                OutlinedButton(
                    onClick = onDestroy,
                    enabled = enabled && vm.state != "shut off",
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Destroy") }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    type: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = singleLine,
    minLines = if (singleLine) 1 else 4,
    keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
    visualTransformation = if (type == KeyboardType.Password) PasswordVisualTransformation() else VisualTransformation.None,
    modifier = Modifier.fillMaxWidth(),
)

@Composable
private fun BackButton(onBack: () -> Unit) =
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }

@Composable
private fun Placeholder(text: String, modifier: Modifier = Modifier) = Box(modifier.fillMaxSize(), Alignment.Center) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val Server.title get() = name.ifBlank { host }
