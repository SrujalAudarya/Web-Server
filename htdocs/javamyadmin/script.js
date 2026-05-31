let selectedDatabase = null;
let selectedTable = null;
let databases = [];

// =========================
// COMMON API FUNCTION
// =========================

async function api(url, options = {}) {
    try {
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

    } catch (error) {
        console.error("API Error:", error);

        return {
            error: "Unable to connect backend",
            details: error.message
        };
    }
}

// =========================
// SECTION SWITCHING
// =========================

function showSection(sectionId, button = null) {
    document.querySelectorAll(".section").forEach(section => {
        section.classList.remove("active-section");
    });

    const section = document.getElementById(sectionId);

    if (section) {
        section.classList.add("active-section");
    }

    document.querySelectorAll(".menu-btn").forEach(btn => {
        btn.classList.remove("active");
    });

    if (button) {
        button.classList.add("active");
    }

    if (sectionId === "dashboard") {
        refreshAll();
    }

    if (sectionId === "databases") {
        loadDatabases();
    }

    if (sectionId === "tables") {
        loadTables();
    }
}

function openSection(sectionId) {
    const btn = document.querySelector(`.menu-btn[data-section="${sectionId}"]`);
    showSection(sectionId, btn);
}

// =========================
// DASHBOARD
// =========================

async function refreshAll() {
    await checkServerStatus();
    await loadDatabases(false);
}

async function checkServerStatus() {
    const result = await api("/api/status");

    if (result && result.status === "running") {
        setServerStatus("Running", "Connected", true);
    } else {
        setServerStatus("Error", "Disconnected", false);
    }
}

function setServerStatus(serverText, mysqlText, online) {
    const serverStatus = document.getElementById("serverStatus");
    const mysqlStatus = document.getElementById("mysqlStatus");

    if (serverStatus) {
        serverStatus.innerText = serverText;
    }

    if (mysqlStatus) {
        mysqlStatus.innerText = mysqlText;
        mysqlStatus.className = online ? "status online" : "status offline";
    }
}

// =========================
// DATABASES
// =========================

async function loadDatabases(openDbSection = true) {
    const tbody = document.getElementById("databaseList");

    if (tbody) {
        tbody.innerHTML = `
            <tr>
                <td colspan="3" class="empty">Loading databases...</td>
            </tr>
        `;
    }

    const result = await api("/api/javamyadmin/databases");

    console.log("Databases:", result);

    if (!Array.isArray(result)) {
        if (tbody) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="3" class="empty">
                        Failed to load databases<br>
                        ${result.error || result.message || "Unknown error"}
                    </td>
                </tr>
            `;
        }

        document.getElementById("databaseCount").innerText = "0";
        setServerStatus("Running", "MySQL Error", false);
        return;
    }

    databases = result;

    document.getElementById("databaseCount").innerText = databases.length;
    setServerStatus("Running", "MySQL Connected", true);

    if (!tbody) return;

    if (databases.length === 0) {
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
                    <button class="small-btn" onclick="selectDatabase('${escapeJs(db)}')">Open</button>
                    <button class="small-btn danger" onclick="dropDatabase('${escapeJs(db)}')">Drop</button>
                </td>
            </tr>
        `;
    });
}

async function createDatabase() {
    const input = document.getElementById("databaseName");
    const dbName = input.value.trim();

    if (!dbName) {
        alert("Enter database name");
        return;
    }

    const result = await api("/api/javamyadmin/create-database", {
        method: "POST",
        body: JSON.stringify({ db: dbName })
    });

    alert(result.message || result.error || "Done");

    input.value = "";
    loadDatabases();
}

async function dropDatabase(db) {
    if (!confirm("Are you sure you want to drop database: " + db + "?")) {
        return;
    }

    const result = await api("/api/javamyadmin/drop-database", {
        method: "DELETE",
        body: JSON.stringify({ db })
    });

    alert(result.message || result.error || "Done");

    if (selectedDatabase === db) {
        selectedDatabase = null;
        selectedTable = null;
        updateSelectedCards();
    }

    loadDatabases();
}

// =========================
// TABLES
// =========================

async function selectDatabase(db) {
    selectedDatabase = db;
    selectedTable = null;

    updateSelectedCards();

    document.getElementById("selectedDatabaseText").innerText =
        "Selected Database: " + db;

    openSection("tables");

    await loadTables();
}

async function loadTables() {
    const tbody = document.getElementById("tableList");

    if (!tbody) return;

    if (!selectedDatabase) {
        tbody.innerHTML = `
            <tr>
                <td colspan="3" class="empty">Select database first</td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = `
        <tr>
            <td colspan="3" class="empty">Loading tables...</td>
        </tr>
    `;

    const result = await api(
        "/api/javamyadmin/tables?db=" + encodeURIComponent(selectedDatabase)
    );

    console.log("Tables:", result);

    if (!Array.isArray(result)) {
        tbody.innerHTML = `
            <tr>
                <td colspan="3" class="empty">
                    Failed to load tables<br>
                    ${result.error || result.message || "Unknown error"}
                </td>
            </tr>
        `;
        return;
    }

    if (result.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="3" class="empty">No tables found</td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = "";

    result.forEach((table, index) => {
        tbody.innerHTML += `
            <tr>
                <td>${index + 1}</td>
                <td>${table}</td>
                <td>
                    <button class="small-btn" onclick="selectTable('${escapeJs(table)}')">View</button>
                    <button class="small-btn" onclick="renameTable('${escapeJs(table)}')">Rename</button>
                    <button class="small-btn danger" onclick="dropTable('${escapeJs(table)}')">Drop</button>
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

    document.getElementById("newTableName").value = "";
    document.getElementById("newTableColumns").value = "";

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

    if (selectedTable === table) {
        selectedTable = null;
        updateSelectedCards();
        document.getElementById("recordsBox").innerHTML =
            "Select a table to view records.";
    }

    loadTables();
}

async function renameTable(table) {
    const newTable = prompt("Enter new table name:", table + "_new");

    if (!newTable) return;

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

    updateSelectedCards();

    document.getElementById("selectedTableText").innerText =
        "Selected Table: " + table;

    await loadRecords();
}

// =========================
// RECORDS
// =========================

async function loadRecords() {
    const box = document.getElementById("recordsBox");

    if (!selectedDatabase || !selectedTable) {
        box.innerHTML = "Select database and table first.";
        return;
    }

    box.innerHTML = "Loading records...";

    const result = await api(
        "/api/javamyadmin/records?db="
        + encodeURIComponent(selectedDatabase)
        + "&table="
        + encodeURIComponent(selectedTable)
    );

    renderTable(box, result);
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

    if (!confirm("Remove all rows from " + selectedTable + "?")) {
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

// =========================
// ROW CRUD
// =========================

function readRowJson() {
    const text = document.getElementById("rowJson").value.trim();

    if (!text) {
        alert("Enter row JSON");
        return null;
    }

    try {
        return JSON.parse(text);
    } catch (error) {
        alert("Invalid JSON format");
        return null;
    }
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

// =========================
// SQL QUERY
// =========================

async function runQuery() {
    const sql = document.getElementById("sqlQuery").value.trim();

    if (!sql) {
        alert("Write SQL query");
        return;
    }

    const box = document.getElementById("queryResult");
    box.innerHTML = "Running query...";

    const result = await api("/api/javamyadmin/query", {
        method: "POST",
        body: JSON.stringify({ sql })
    });

    renderResult("queryResult", result);
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
        body: JSON.stringify({
            name,
            sql
        })
    });

    alert(result.message || result.error || "Done");

    loadSavedQueries();
}

async function loadSavedQueries() {
    const box = document.getElementById("savedQueriesBox");
    box.innerHTML = "Loading saved queries...";

    const result = await api("/api/javamyadmin/saved-queries");

    if (!Array.isArray(result) || result.length === 0) {
        box.innerHTML = "No saved queries.";
        return;
    }

    box.innerHTML = "";

    result.forEach(item => {
        const queryName = item.name.replace(".sql", "");

        box.innerHTML += `
            <div class="query-item">
                <span>${item.name}</span>
                <button onclick="runSavedQuery('${escapeJs(queryName)}')">Run</button>
            </div>
        `;
    });
}

async function runSavedQuery(name) {
    const result = await api(
        "/api/javamyadmin/run-saved-query?name="
        + encodeURIComponent(name)
    );

    renderResult("queryResult", result);
    openSection("query");
}

async function loadQueryHistory() {
    const box = document.getElementById("queryHistoryBox");
    box.innerHTML = "Loading history...";

    const result = await api("/api/javamyadmin/query-history");

    if (!Array.isArray(result) || result.length === 0) {
        box.innerHTML = "No query history.";
        return;
    }

    box.innerHTML = result
        .map(line => `<div class="history-line">${line}</div>`)
        .join("");
}

function clearQuery() {
    document.getElementById("sqlQuery").value = "";
    document.getElementById("queryResult").innerHTML =
        "Result will appear here...";
}

// =========================
// IMPORT EXPORT
// =========================

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
    const lines = text.trim().split(/\r?\n/);

    const columns = lines[0]
        .split(",")
        .map(value => cleanCsv(value));

    const rows = lines.slice(1).map(line => {
        return line.split(",").map(value => cleanCsv(value));
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

    downloadFile(
        selectedTable + ".json",
        JSON.stringify(result, null, 2)
    );
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

    downloadFile(
        selectedDatabase + ".json",
        JSON.stringify(result, null, 2)
    );
}

// =========================
// RENDER HELPERS
// =========================

function renderResult(elementId, result) {
    const box = document.getElementById(elementId);

    if (!box) return;

    if (Array.isArray(result)) {
        renderTable(box, result);
        return;
    }

    if (typeof result === "object" && result !== null) {
        box.innerHTML = `<pre>${escapeHtml(JSON.stringify(result, null, 2))}</pre>`;
        return;
    }

    box.innerText = result;
}

function renderTable(container, data) {
    if (!container) return;

    if (!Array.isArray(data)) {
        container.innerHTML =
            `<pre>${escapeHtml(JSON.stringify(data, null, 2))}</pre>`;
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
                    ${columns.map(col => `<th>${escapeHtml(col)}</th>`).join("")}
                </tr>
            </thead>
            <tbody>
    `;

    data.forEach(row => {
        html += `
            <tr>
                ${columns.map(col => `<td>${escapeHtml(row[col] ?? "")}</td>`).join("")}
            </tr>
        `;
    });

    html += `
            </tbody>
        </table>
    `;

    container.innerHTML = html;
}

function updateSelectedCards() {
    document.getElementById("selectedDbCard").innerText =
        selectedDatabase || "None";

    document.getElementById("selectedTableCard").innerText =
        selectedTable || "None";
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

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function escapeJs(value) {
    return String(value)
        .replaceAll("\\", "\\\\")
        .replaceAll("'", "\\'");
}

// =========================
// INIT
// =========================

document.addEventListener("DOMContentLoaded", () => {
    showSection("dashboard", document.querySelector(".menu-btn.active"));
    refreshAll();
});