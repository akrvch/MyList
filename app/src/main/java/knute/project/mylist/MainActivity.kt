package knute.project.mylist

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete as RoomDelete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import knute.project.mylist.ui.theme.MyListTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyListTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        ShoppingListScreen()
                    }
                }
            }
        }
    }
}


@Entity(tableName = "shopping_items")
data class ShoppingItem(
    val name: String,
    val isBought: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Int = 0
)


@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY id DESC")
    fun getAllItems(): List<ShoppingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: ShoppingItem)

    @Update
    fun updateItem(item: ShoppingItem)

    @RoomDelete
    fun deleteItem(item: ShoppingItem)
}


@Database(entities = [ShoppingItem::class], version = 1)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        fun getInstance(context: Context): ShoppingDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_db"
                ).build().also { INSTANCE = it }
            }
    }
}


class ShoppingListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ShoppingDatabase.getInstance(application).shoppingDao()
    private val _shoppingList = mutableStateListOf<ShoppingItem>()
    val shoppingList: List<ShoppingItem> get() = _shoppingList

    init { loadShoppingList() }

    private fun loadShoppingList() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = dao.getAllItems()
            _shoppingList.clear()
            _shoppingList.addAll(items)
        }
    }

    fun addItem(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertItem(ShoppingItem(name = name))
            loadShoppingList()
        }
    }

    fun toggleBought(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _shoppingList[index]
            val updated = item.copy(isBought = !item.isBought)
            dao.updateItem(updated)
            _shoppingList[index] = updated
        }
    }

    fun deleteItem(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _shoppingList[index]
            dao.deleteItem(item)
            loadShoppingList()
        }
    }

    fun editItem(index: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _shoppingList[index]
            val updated = item.copy(name = newName)
            dao.updateItem(updated)
            _shoppingList[index] = updated
        }
    }
}

class ShoppingListViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShoppingListViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun ShoppingItemCard(
    item: ShoppingItem,
    onToggleBought: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(Color.LightGray, MaterialTheme.shapes.large)
            .clickable { onToggleBought() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isBought,
            onCheckedChange = { onToggleBought() }
        )
        Text(
            text = item.name,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            fontSize = 18.sp
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit item")
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete item")
        }
    }
}

@Composable
fun AddItemButton(addItem: (String) -> Unit = {}) {
    var text by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Add Item") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    addItem(text)
                    text = ""
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Add")
        }
    }
}


@Composable
fun ShoppingListScreen(
    viewModel: ShoppingListViewModel = viewModel(
        factory = ShoppingListViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    var editingIndex by remember { mutableStateOf(-1) }
    var editingText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            AddItemButton { viewModel.addItem(it) }
        }
        itemsIndexed(viewModel.shoppingList) { ix, item ->
            if (ix == editingIndex) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(Color.White, MaterialTheme.shapes.medium)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (editingText.isNotBlank()) viewModel.editItem(ix, editingText)
                        editingIndex = -1
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                    IconButton(onClick = { editingIndex = -1 }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            } else {
                ShoppingItemCard(
                    item = item,
                    onToggleBought = { viewModel.toggleBought(ix) },
                    onEdit = {
                        editingIndex = ix
                        editingText = item.name
                    },
                    onDelete = { viewModel.deleteItem(ix) }
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ShoppingListScreenPreview() {
    ShoppingListScreen()
}
