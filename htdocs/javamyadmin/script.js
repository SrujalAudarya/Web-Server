let selectedDatabase = null;

function showSection(sectionId) {
    const sections = document.querySelectorAll(".section");
    const links = document.querySelectorAll(".menu a");

    sections.forEach(section => {
        section.classList.remove("active-section");
    });

    links.forEach(link => {
        link.classList.remove("active");
    });

    document.getElementById(sectionId).classList.add("active-section");

    event.target.classList.add("active");
}

function refreshStatus() {
    alert("Server status refreshed!");
}

function createDatabase() {
    const dbName = document.getElementById("databaseName").value.trim();

    if (!dbName) {
        alert("Please enter database name");
        return;
    }

    alert("Create database API will be connected later: " + dbName);
}

function selectDatabase(name) {
    selectedDatabase = name;

    document.getElementById("selectedDatabaseText").innerText =
        "Selected Database: " + name;

    showSectionManually("tables");

    const tableList = document.getElementById("tableList");

    tableList.innerHTML = `
        <tr>
            <td>1</td>
            <td>users</td>
            <td>0</td>
            <td>
                <button class="small-btn" onclick="viewTable('users')">View</button>
                <button class="small-btn danger">Delete</button>
            </td>
        </tr>
        <tr>
            <td>2</td>
            <td>products</td>
            <td>0</td>
            <td>
                <button class="small-btn" onclick="viewTable('products')">View</button>
                <button class="small-btn danger">Delete</button>
            </td>
        </tr>
    `;
}

function showSectionManually(sectionId) {
    const sections = document.querySelectorAll(".section");

    sections.forEach(section => {
        section.classList.remove("active-section");
    });

    document.getElementById(sectionId).classList.add("active-section");
}

function viewTable(tableName) {
    const recordsBox = document.getElementById("recordsBox");

    recordsBox.innerText =
        "Records from table: " + tableName + "\n\n" +
        "This will be loaded from JavaMyAdmin API later.";
}

function runQuery() {
    const query = document.getElementById("sqlQuery").value.trim();

    if (!query) {
        alert("Please write SQL query");
        return;
    }

    document.getElementById("queryResult").innerText =
        "Query sent:\n\n" + query +
        "\n\nBackend SQL API will be connected in next step.";
}

function clearQuery() {
    document.getElementById("sqlQuery").value = "";
    document.getElementById("queryResult").innerText =
        "Result will appear here...";
}

function importFile() {
    const fileInput = document.getElementById("importFile");

    if (!fileInput.files.length) {
        alert("Please select a file");
        return;
    }

    alert("Import file selected: " + fileInput.files[0].name);
}

function exportDatabase() {
    alert("Export database API will be connected later.");
}

function exportTable() {
    alert("Export table API will be connected later.");
}

function testConnection() {
    alert("Testing MySQL connection...");
}