let selectedDatabase = null;
let selectedTable = null;
let databases = [];

function showSection(sectionId, button = null) {
    document.querySelectorAll(".section").forEach(section => {
        section.style.display = "none";
        section.classList.remove("active-section");
    });

    const target = document.getElementById(sectionId);

    if (target) {
        target.style.display = "block";
        target.classList.add("active-section");
    }

    document.querySelectorAll(".menu a, .menu-btn").forEach(item => {
        item.classList.remove("active");
    });

    if (button) {
        button.classList.add("active");
    }

    if (sectionId === "databases") {
        loadDatabases();
    }

    if (sectionId === "tables" && selectedDatabase) {
        loadTables();
    }
}

function openSection(sectionId) {
    showSection(sectionId);
}

async function api(url, options = {}) {
    const response = await fetch(url, {
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        },
        ...options
    });

    const text = await response.text();

    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

async function refreshAll() {
    await checkServerStatus();
    await loadDatabases();
}

async function checkServerStatus() {
    try {
        const result = await api("/api/status");

        document.getElementById("serverStatus").innerText = "Running";
        document.getElementById("mysqlStatus").innerText = "Connected";
        document.getElementById("mysqlStatus").className = "status online";

        console.log(result);
    } catch (error) {
        document.getElementById("serverStatus").innerText = "Error";
        document.getElementById("mysqlStatus").innerText = "Disconnected";
        document.getElementById("mysqlStatus").className = "status offline";
    }
}

async function loadDatabases() {
    const tbody = document.getElementById("databaseList");

    tbody.innerHTML = `
        <tr>
            <td colspan="3" class="empty">Loading databases...</td>
        </tr>
    `;

    try {
        databases = await api("/api/javamyadmin/databases");

        document.getElementById("databaseCount").innerText = databases.length;

        if (!Array.isArray(databases) || databases.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="3" class="empty">No databases found</td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = "";

        databases.forEach((db, index) => {
            tbody.innerHTML += `
                <tr>
                    <td>${index + 1}</td>
                    <td>${db}</td>
                    <td>
                        <button class="small-btn" onclick="selectDatabase('${db}')">Open</button>
                        <button class="small-btn danger" onclick="dropDatabase('${db}')">Drop</button>
                    </td>
                </tr>
            `;
        });

    } catch (error) {
        tbody.innerHTML = `
            <tr>
                <td colspan="3" class="empty">Failed to load databases</td>
            </tr>
        `;
    }
}

async function createDatabase() {
    const dbName = document.getElementById("databaseName").value.trim();

    if (!dbName) {
        alert("Enter database name");
        return;
    }

    const result = await api("/api/javamyadmin/create-database", {
        method: "POST",
        body: JSON.stringify({ db: dbName })
    });

    alert(result.message || result.error || "Done");
    document.getElementById("databaseName").value = "";
    loadDatabases();
}

async function dropDatabase(db) {
    if (!confirm("Drop database: " + db + "?")) {
        return;
    }

    const result = await api("/api/javamyadmin/drop-database", {
        method: "DELETE",
        body: JSON.stringify({ db })
    });

    alert(result.message || result.error || "Done");
    loadDatabases();
}

async function selectDatabase(db) {
    selectedDatabase = db;
    selectedTable = null;

    document.getElementById("selectedDatabaseText").innerText =
        "Selected Database: " + db;

    document.getElementById("selectedDbCard").innerText = db;
    document.getElementById("selectedTableCard").innerText = "None";
    document.getElementById("selectedTableText").innerText = "Selected Table: none";

    openSection("tables");

    await loadTables();
}

async function loadTables() {
    const tbody = document.getElementById("tableList");

    if (!selectedDatabase) {
        tbody.innerHTML = `
            <tr>
                <td colspan="3" class="empty">No database selected</td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = `
        <tr>
            <td colspan="3" class="empty">Loading tables...</td>
        </tr>
    `;

    const tables = await api(
        "/api/javamyadmin/tables?db=" + encodeURIComponent(selectedDatabase)
    );

    if (!Array.isArray(tables) || tables.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="3" class="empty">No tables found</td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = "";

    tables.forEach((table, index) => {
        tbody.innerHTML += `
            <tr>
                <td>${index + 1}</td>
                <td>${table}</td>
                <td>
                    <button class="small-btn" onclick="selectTable('${table}')">View</button>
                    <button class="small-btn" onclick="renameTable('${table}')">Rename</button>
                    <button class="small-btn danger" onclick="dropTable('${table}')">Drop</button>
                </td>
            </tr>
        `;
    });
}

async function createTable() {
    if (!selectedDatabase) {
        alert("Select database first");
        return;
    }

    const table = document.getElementById("newTableName").value.trim();
    const columns = document.getElementById("newTableColumns").value.trim();

    if (!table || !columns) {
        alert("Enter table name and columns");
        return;
    }

    const result = await api("/api/javamyadmin/create-table", {
        method: "POST",
        body: JSON.stringify({
            db: selectedDatabase,
            table,
            columns
        })
    });

    alert(result.message || result.error || "Done");
    loadTables();
}

async function dropTable(table) {
    if (!selectedDatabase) {
        alert("Select database first");
        return;
    }

    if (!confirm("Drop table: " + table + "?")) {
        return;
    }

    const result = await api("/api/javamyadmin/drop-table", {
        method: "DELETE",
        body: JSON.stringify({
            db: selectedDatabase,
            table
        })
    });

    alert(result.message || result.error || "Done");
    loadTables();
}

async function renameTable(table) {
    const newTable = prompt("New table name:", table + "_new");

    if (!newTable) {
        return;
    }

    const result = await api("/api/javamyadmin/rename-table", {
        method: "PUT",
        body: JSON.stringify({
            db: selectedDatabase,
            oldTable: table,
            newTable
        })
    });

    alert(result.message || result.error || "Done");
    loadTables();
}

async function selectTable(table) {
    selectedTable = table;

    document.getElementById("selectedTableCard").innerText = table;
    document.getElementById("selectedTableText").innerText =
        "Selected Table: " + table;

    await loadRecords();
}

async function loadRecords() {
    const box = document.getElementById("recordsBox");

    if (!selectedDatabase || !selectedTable) {
        box.innerHTML = "Select database and table first.";
        return;
    }

    box.innerHTML = "Loading records...";

    const records = await api(
        "/api/javamyadmin/records?db="
        + encodeURIComponent(selectedDatabase)
        + "&table="
        + encodeURIComponent(selectedTable)
    );

    renderTable(box, records);
}

async function describeTable() {
    const box = document.getElementById("recordsBox");

    if (!selectedDatabase || !selectedTable) {
        alert("Select table first");
        return;
    }

    const result = await api(
        "/api/javamyadmin/describe-table?db="
        + encodeURIComponent(selectedDatabase)
        + "&table="
        + encodeURIComponent(selectedTable)
    );

    renderTable(box, result);
}

async function truncateTable() {
    if (!selectedDatabase || !selectedTable) {
        alert("Select table first");
        return;
    }

    if (!confirm("Remove all records from " + selectedTable + "?")) {
        return;
    }

    const result = await api("/api/javamyadmin/truncate-table", {
        method: "DELETE",
        body: JSON.stringify({
            db: selectedDatabase,
            table: selectedTable
        })
    });

    alert(result.message || result.error || "Done");
    loadRecords();
}

async function insertRow() {
    if (!selectedDatabase || !selectedTable) {
        alert("Select table first");
        return;
    }

    const row = readRowJson();

    if (!row) return;

    const result = await api("/api/javamyadmin/insert-row", {
        method: "POST",
        body: JSON.stringify({
            db: selectedDatabase,
            table: selectedTable,
            row
        })
    });

    alert(result.message || result.error || "Done");
    loadRecords();
}

async function updateRow() {
    if (!selectedDatabase || !selectedTable) {
        alert("Select table first");
        return;
    }

    const row = readRowJson();
    const idColumn = document.getElementById("idColumn").value.trim();
    const idValue = document.getElementById("idValue").value.trim();

    if (!row || !idColumn || !idValue) {
        alert("Row JSON, ID column and ID value required");
        return;
    }

    const result = await api("/api/javamyadmin/update-row", {
        method: "PUT",
        body: JSON.stringify({
            db: selectedDatabase,
            table: selectedTable,
            idColumn,
            idValue,
            row
        })
    });

    alert(result.message || result.error || "Done");
    loadRecords();
}

async function deleteRow() {
    if (!selectedDatabase || !selectedTable) {
        alert("Select table first");
        return;
    }

    const idColumn = document.getElementById("idColumn").value.trim();
    const idValue = document.getElementById("idValue").value.trim();

    if (!idColumn || !idValue) {
        alert("ID column and ID value required");
        return;
    }

    const result = await api("/api/javamyadmin/delete-row", {
        method: "DELETE",
        body: JSON.stringify({
            db: selectedDatabase,
            table: selectedTable,
            idColumn,
            idValue
        })
    });

    alert(result.message || result.error || "Done");
    loadRecords();
}

function readRowJson() {
    try {
        return JSON.parse(document.getElementById("rowJson").value.trim());
    } catch {
        alert("Invalid row JSON");
        return null;
    }
}

async function runQuery() {

    const sql =
        document.getElementById("sqlQuery")
            .value
            .trim();

    if (!sql) {
        alert("Write SQL query");
        return;
    }

    try {

        const result = await api(
            "/api/javamyadmin/query",
            {
                method: "POST",
                body: JSON.stringify({
                    sql: sql
                })
            }
        );

        console.log("SQL Result:", result);

        renderResult("queryResult", result);

    } catch (error) {

        console.error(error);

        document.getElementById("queryResult")
            .innerHTML =
            "<p style='color:red'>Query failed</p>";
    }
}

async function explainQuery() {
    const sql = document.getElementById("sqlQuery").value.trim();

    if (!sql) {
        alert("Write SQL query");
        return;
    }

    const result = await api("/api/javamyadmin/explain-query", {
        method: "POST",
        body: JSON.stringify({ sql })
    });

    renderResult("queryResult", result);
}

async function saveQuery() {
    const sql = document.getElementById("sqlQuery").value.trim();

    if (!sql) {
        alert("Write SQL query");
        return;
    }

    const name = prompt("Save query name:");

    if (!name) return;

    const result = await api("/api/javamyadmin/save-query", {
        method: "POST",
        body: JSON.stringify({ name, sql })
    });

    alert(result.message || result.error || "Done");
    loadSavedQueries();
}

async function loadSavedQueries() {
    const box = document.getElementById("savedQueriesBox");

    const result = await api("/api/javamyadmin/saved-queries");

    if (!Array.isArray(result) || result.length === 0) {
        box.innerHTML = "No saved queries.";
        return;
    }

    box.innerHTML = "";

    result.forEach(item => {
        box.innerHTML += `
            <div class="query-item">
                <span>${item.name}</span>
                <button onclick="runSavedQuery('${item.name.replace(".sql", "")}')">Run</button>
            </div>
        `;
    });
}

async function runSavedQuery(name) {
    const result = await api(
        "/api/javamyadmin/run-saved-query?name=" + encodeURIComponent(name)
    );

    renderResult("queryResult", result);
    openSection("query");
}

async function loadQueryHistory() {
    const box = document.getElementById("queryHistoryBox");

    const result = await api("/api/javamyadmin/query-history");

    if (!Array.isArray(result) || result.length === 0) {
        box.innerHTML = "No query history.";
        return;
    }

    box.innerHTML = result.map(line => `<div>${line}</div>`).join("");
}

function clearQuery() {
    document.getElementById("sqlQuery").value = "";
    document.getElementById("queryResult").innerHTML = "Result will appear here...";
}

async function importSqlFile() {
    const file = document.getElementById("sqlImportFile").files[0];

    if (!file) {
        alert("Select SQL file");
        return;
    }

    const sql = await file.text();

    const result = await api("/api/javamyadmin/import-sql", {
        method: "POST",
        body: JSON.stringify({ sql })
    });

    alert(result.message || result.error || "Done");
}

async function importCsv() {
    const file = document.getElementById("csvImportFile").files[0];
    const db = document.getElementById("csvDb").value.trim();
    const table = document.getElementById("csvTable").value.trim();

    if (!file || !db || !table) {
        alert("CSV file, database and table required");
        return;
    }

    const text = await file.text();
    const lines = text.trim().split("\n");

    const columns = lines[0].split(",").map(v => cleanCsv(v));

    const rows = lines.slice(1).map(line => {
        return line.split(",").map(v => cleanCsv(v));
    });

    const result = await api("/api/javamyadmin/import-csv", {
        method: "POST",
        body: JSON.stringify({
            db,
            table,
            columns,
            rows
        })
    });

    alert(result.message || result.error || "Done");
}

function cleanCsv(value) {
    return value.trim().replace(/^"|"$/g, "");
}

async function exportJson() {
    if (!selectedDatabase || !selectedTable) {
        alert("Select table first");
        return;
    }

    const result = await api(
        "/api/javamyadmin/export-json?db="
        + encodeURIComponent(selectedDatabase)
        + "&table="
        + encodeURIComponent(selectedTable)
    );

    renderResult("exportResult", result);
    downloadFile(selectedTable + ".json", JSON.stringify(result, null, 2));
}

async function exportCsv() {
    if (!selectedDatabase || !selectedTable) {
        alert("Select table first");
        return;
    }

    const result = await api(
        "/api/javamyadmin/export-csv?db="
        + encodeURIComponent(selectedDatabase)
        + "&table="
        + encodeURIComponent(selectedTable)
    );

    if (result.csv) {
        document.getElementById("exportResult").innerText = result.csv;
        downloadFile(selectedTable + ".csv", result.csv);
    } else {
        renderResult("exportResult", result);
    }
}

async function exportDatabaseJson() {
    if (!selectedDatabase) {
        alert("Select database first");
        return;
    }

    const result = await api(
        "/api/javamyadmin/export-database-json?db="
        + encodeURIComponent(selectedDatabase)
    );

    renderResult("exportResult", result);
    downloadFile(selectedDatabase + ".json", JSON.stringify(result, null, 2));
}

function renderResult(elementId, result) {
    const box = document.getElementById(elementId);

    if (Array.isArray(result)) {
        renderTable(box, result);
        return;
    }

    if (typeof result === "object") {
        box.innerText = JSON.stringify(result, null, 2);
        return;
    }

    box.innerText = result;
}

function renderTable(container, data) {
    if (!Array.isArray(data)) {
        container.innerText = JSON.stringify(data, null, 2);
        return;
    }

    if (data.length === 0) {
        container.innerHTML = "No records found.";
        return;
    }

    const columns = Object.keys(data[0]);

    let html = `
        <table class="result-table">
            <thead>
                <tr>
                    ${columns.map(col => `<th>${col}</th>`).join("")}
                </tr>
            </thead>
            <tbody>
    `;

    data.forEach(row => {
        html += `
            <tr>
                ${columns.map(col => `<td>${row[col] ?? ""}</td>`).join("")}
            </tr>
        `;
    });

    html += `
            </tbody>
        </table>
    `;

    container.innerHTML = html;
}

function downloadFile(fileName, content) {
    const blob = new Blob([content], {
        type: "text/plain"
    });

    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = fileName;
    link.click();
}

document.addEventListener("DOMContentLoaded", async () => {

    document.querySelectorAll(".section").forEach(section => {
        section.style.display = "none";
    });

    const dashboard = document.getElementById("dashboard");

    if (dashboard) {
        dashboard.style.display = "block";
    }

    try {
        await checkServerStatus();
        await loadDatabases();
    } catch (e) {
        console.error(e);
    }
});

document.addEventListener("DOMContentLoaded", () => {
    showSection("dashboard", document.querySelector(".menu-btn.active"));
    refreshAll();
});