package org.altekamereren.groggomat

import android.app.Activity
import android.app.DialogFragment
import android.app.ListActivity
import android.app.ListFragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.DataSetObserver
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Typeface
import android.net.wifi.p2p.*
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.FragmentActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import android.support.v7.appcompat
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import org.jetbrains.anko.*
import com.fasterxml.jackson.module.kotlin.*
import jxl.Workbook
import jxl.WorkbookSettings

import kotlinx.android.synthetic.activity_main.*
import org.jetbrains.anko.db.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.*

val PORT = 14156

data class DeviceLatestKryss(val device:String, val latestKryss:Long)
data class KryssPair(val my:DeviceLatestKryss, val their:DeviceLatestKryss?)
data class Kryss(val id:Long?, val device:String, val type:Int, val count:Int, val time:Long, val kamerer:Long, val replaces_id:Long?, val replaces_device:String?) {
    companion object {
        val table = "Kryss k"
        val selectList = arrayOf("ifnull(real_id, _id)", "device", "type", "count", "time", "kamerer", "replaces_id", "replaces_device")
        val parser = rowParser { id:Long, device:String, type:Int, count:Int, time:Long, kamerer:Long, replaces_id:Any?, replaces_device:Any? ->
            Kryss(id, device, type, count, time, kamerer, if(replaces_id is Long) replaces_id else null, if(replaces_device is String) replaces_device else null)
        }
    }
    public fun insert(db: SQLiteDatabase) : Kryss {
        if(id != null) {
            if (replaces_id != null && replaces_device != null) {
                db.insert("Kryss",
                        "real_id" to id,
                        "device" to device,
                        "type" to type,
                        "count" to count,
                        "time" to time,
                        "kamerer" to kamerer,
                        "replaces_id" to replaces_id,
                        "replaces_device" to replaces_device
                )
            } else {
                db.insert("Kryss",
                        "real_id" to id,
                        "device" to device,
                        "type" to type,
                        "count" to count,
                        "time" to time,
                        "kamerer" to kamerer
                )
            }
            return this
        } else {
            val id = if (replaces_id != null && replaces_device != null) {
                db.insert("Kryss",
                        "device" to device,
                        "type" to type,
                        "count" to count,
                        "time" to time,
                        "kamerer" to kamerer,
                        "replaces_id" to replaces_id,
                        "replaces_device" to replaces_device
                )
            } else {
                db.insert("Kryss",
                        "device" to device,
                        "type" to type,
                        "count" to count,
                        "time" to time,
                        "kamerer" to kamerer
                )
            }
            return Kryss(id, device, type, count, time, kamerer, replaces_id, replaces_device)
        }
    }
}

class Kamerer(val id: Long, val name: String, var weight: Double?, val male: Boolean, var updated: Long) {
    var alcohol:Double? = null
    val kryss = Array(KryssType.types.size(), { i -> 0 })
    public fun update(db: SQLiteDatabase) {
        val weight = weight
        if(weight != null) {
            db.update("Kamerer",
                    "name" to name,
                    "weight" to weight,
                    "male" to male,
                    "updated" to updated)
                    .where("_id = {id}", "id" to id)
                    .exec()
        } else {
            db.update("Kamerer",
                    "name" to name,
                    "male" to if(male) 1 else 0,
                    "updated" to updated)
                    .where("_id = {id}", "id" to id)
                    .exec()
        }
    }
}


public class MainActivity : Activity(), AnkoLogger {
    var intentFilter : IntentFilter = IntentFilter()
    var receiver: WiFiDirectBroadcastReceiver? = null
    var deviceId: String = ""

    private var updater: ScheduledFuture<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super<Activity>.onCreate(savedInstanceState)

        val prefs = getPreferences(Context.MODE_PRIVATE)
        deviceId = prefs.getString("deviceId","")
        if(deviceId == "") {
            deviceId = UUID.randomUUID().toString()
            val editor = prefs.edit()
            editor.putString("deviceId", deviceId)
            editor.commit()
        }

        loadKamererer()
        loadKryss()

        intentFilter = IntentFilter()
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        val manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, getMainLooper(), null)
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)

        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, KamererListFragment())
                .commit()
    }

    data class IdAndDevice(val id:Long, val device:String)

    var kryssCache:ArrayList<Kryss> = ArrayList<Kryss>()

    private fun loadKryss() {
        database.use {
            info("Loading kryss")
            val time = System.currentTimeMillis()
            val rows = select("Kryss k", *Kryss.selectList).parseList(Kryss.parser)
            val replacedRows = HashMap<IdAndDevice, Kryss>()
            for(row in rows) {
                if(row.replaces_id != null && row.replaces_device != null) {
                    val replaces = IdAndDevice(row.replaces_id, row.replaces_device)
                    if(replacedRows.containsKey(replaces)) {
                        // Pick one device to win
                        if(row.device > replacedRows[replaces].device) {
                            replacedRows[replaces] = row
                        }
                    } else {
                        replacedRows[replaces] = row
                    }
                }
            }

            // Skip replacing rows that were not picked, then filter replaced rows.
            kryssCache = (rows.filter { r -> r.replaces_id == null} + replacedRows.values()).filter { r -> !replacedRows.containsKey(IdAndDevice(r.id as Long, r.device))}.toArrayList()
            info("Time to load kryss: ${System.currentTimeMillis() - time}")
        }
        calculateKryss()
    }

    private fun calculateKryss() {
        database.use {
            for(kamerer in kamererer.values()) {
                for(i in kamerer.kryss.indices) {
                    kamerer.kryss[i] = 0
                }
                kamerer.alcohol = null
            }
            info("Calculating kryss")
            val time = System.currentTimeMillis()
            for (kryss in kryssCache) {
                val kamerer = kamererer[kryss.kamerer]
                val kryssType = KryssType.types[kryss.type]
                kamerer.kryss[kryss.type] += kryss.count
                val weight = kamerer.weight
                if(weight != null) {
                    kamerer.alcohol = (kamerer.alcohol ?: 0.0) +
                            /*Math.min(1.0, (time - kryss.time) / (30 * 60 * 1000.0)) */
                            10.0 * Math.max(0.0, 0.806 * kryssType.alcohol * 25.0 * 0.1 * 1.2 * kryss.count
                                    / ((if (kamerer.male) 0.58 else 0.49) * weight)
                                    - (if (kamerer.male) 0.015 else 0.017) * (time - kryss.time) / 3600000)
                }
            }
            info("Time to calc kryss: ${System.currentTimeMillis() - time}")
        }
    }

    val kamererer = HashMap<Long, Kamerer>()
    private fun loadKamererer() {
        kamererer.clear()
        database.use {
            val kamererParser = rowParser { id: Long, name: String, weight: Any?, male: Long, updated:Long -> Kamerer(id, name, if(weight is Double) weight else null, male > 0, updated) }
            for (kamerer in select("Kamerer", "_id", "name", "weight", "male", "updated").parseList(kamererParser)) {
                kamererer[kamerer.id] = kamerer
            }
        }
    }

    override fun onDestroy() {
        super<Activity>.onDestroy()
    }

    override fun onStart() {
        super<Activity>.onStart()
        startServer()
    }

    override fun onStop() {
        super<Activity>.onStop()
        serverTask?.cancel(true)
    }

    override fun onResume() {
        super<Activity>.onResume()
        registerReceiver(receiver, intentFilter)
        val sch = Executors.newScheduledThreadPool(1) as ScheduledThreadPoolExecutor
        updater = sch.scheduleAtFixedRate({ uiThread { updateKryssLists(false) }}, 60, 60, TimeUnit.SECONDS) ;
    }

    override fun onPause() {
        super<Activity>.onPause()
        unregisterReceiver(receiver)
        updater?.cancel(true)
    }

    val syncing = Semaphore(1, true)

    fun syncKryss(outputStream:OutputStream, inputStream:InputStream) {
        syncing.acquire()
        try {
            database.use {
                val myLatestKryss =
                        select("Kryss", "device", "max(ifnull(real_id, _id))")
                                .groupBy("device")
                                .parseList(rowParser({ device: String, id: Long -> DeviceLatestKryss(device, id) }))

                val mapper = jacksonObjectMapper()
                mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
                mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)

                val protocol = "groggomat 2"
                mapper.writeValue(outputStream, protocol)
                outputStream.flush()
                val theirProtocol = mapper.readValue<String>(inputStream)
                if (protocol != theirProtocol) {
                    throw Exception("Unknown protocol: $theirProtocol")
                }

                info("Sending ${myLatestKryss}")
                mapper.writeValue(outputStream, myLatestKryss)
                outputStream.flush()

                val jsonParser = mapper.getFactory().createParser(inputStream)
                val theirLatestKryss = jsonParser.readValueAs<List<DeviceLatestKryss>>(object : TypeReference<List<DeviceLatestKryss>>() {})
                info("Received ${theirLatestKryss}")

                val kryssPairs = myLatestKryss.map { my -> KryssPair(my, theirLatestKryss.firstOrNull({ their -> their.device == my.device })) }

                val newerKryss = kryssPairs.filter { p -> p.their == null || p.my.latestKryss > p.their.latestKryss }.map { p -> DeviceLatestKryss(p.my.device, p.their?.latestKryss ?: 0) }

                val kryssToSend = newerKryss.flatMap { newer ->
                    select(Kryss.table, *Kryss.selectList)
                            .where("device = {device} and ifnull(real_id, _id) > {latestKryss}",
                                    "device" to newer.device,
                                    "latestKryss" to newer.latestKryss)
                            .parseList(Kryss.parser)
                }

                info("Sending ${kryssToSend.size()} kryss")
                mapper.writeValue(outputStream, kryssToSend)
                outputStream.flush()

                val theirKryss = jsonParser.readValueAs<List<Kryss>>(object : TypeReference<List<Kryss>>() {})
                info("Received ${theirKryss.size()} kryss")

                info("Sending ${kamererer.size()} kamererer")
                mapper.writeValue(outputStream, kamererer.values())
                outputStream.flush()

                val theirKamererer = jsonParser.readValueAs<List<Kamerer>>(object : TypeReference<List<Kamerer>>() {})
                info("Received ${theirKamererer.size()} kamererer")

                info("Storing updates")

                for (kryss in theirKryss) {
                    kryss.insert(this)
                }
                for (kamerer in theirKamererer) {
                    if (kamerer.updated > kamererer[kamerer.id].updated) {
                        kamererer[kamerer.id].weight = kamerer.weight
                        kamererer[kamerer.id].update(this)
                    }
                }

                info("Sync complete")
            }
        } finally {
            syncing.release()
        }
    }

    private var serverTask: Future<Unit>? = null

    fun startServer() {
        serverTask = async {
            ServerSocket(PORT).use { serverSocket ->
                info("Server listening on port $PORT")
                while (serverTask?.isCancelled() == false) {
                    try {
                        serverSocket.accept().use { client ->
                            client.setSoTimeout(5*1000)

                            info("Client connected, syncing")
                            syncKryss(client.getOutputStream(), client.getInputStream())
                        }
                        uiThread {
                            toast("Sync done as server")
                            loadKamererer()
                            updateKryssLists(true)
                        }
                    } catch(e: Exception) {
                        if(e !is InterruptedException){
                            error(e.toString())
                            uiThread {
                                toast("Error syncing: $e")
                            }
                        }
                    }
                }
            }
        }
    }

    fun startClient(host:String) {
        async {
            try{
                info("Starting Client")
                Socket().use { socket ->
                    socket.bind(null)
                    socket.connect(InetSocketAddress(host, PORT), 500)
                    socket.setSoTimeout(10000)
                    info("Connected to server")
                    syncKryss(socket.getOutputStream(), socket.getInputStream())
                }
                uiThread {
                    toast("Sync done as client")
                    loadKamererer()
                    updateKryssLists(true)
                }
            } catch(e:Exception) {
                error(e.toString())
                uiThread {
                    toast("Error syncing: $e")
                }
            }

        }
    }

    private var menu: Menu? = null

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu)
        this.menu = menu
        updateMenu()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item!!.getItemId()

        val wifiP2pInfo = wifiP2pInfo
        val receiver = receiver
        if(id == R.id.action_connect && receiver != null) {
            if (wifiP2pInfo?.groupFormed == true) {
                //Disconnect
                receiver.manager.removeGroup(receiver.channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                    }

                    override fun onFailure(reason: Int) {
                        toast("Failed to disconenct: $reason")
                    }
                })
            } else {
                searchingForPeers = true
                toast("Searching for peers")
                receiver.manager.discoverPeers(receiver.channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                    }

                    override fun onFailure(reason: Int) {
                        searchingForPeers = false
                        toast("Failed to discover peers: $reason")
                    }
                })
            }

            return true
        }

        if (id == R.id.action_sync && wifiP2pInfo != null) {
            startClient(wifiP2pInfo.groupOwnerAddress.getHostAddress())
            return true
        }

        if (id == R.id.action_export) {
            val date = SimpleDateFormat("yyyyMMdd-hhmmss").format(Date())
            val dir = File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/groggomat")
            dir.mkdirs()

            val file = File(dir, "$date.xls")

            val wbSettings = WorkbookSettings()
            wbSettings.setLocale(Locale("en", "EN"))
            val workbook = Workbook.createWorkbook(file, wbSettings)
            val sheet = workbook.createSheet("Kryss", 0)
            val cols = arrayOf("kamerer", "time", "type", "count")
            for(i in cols.indices) {
                sheet.addCell(jxl.write.Label(i, 0, cols[i]))
            }
            database.use {
                for (i in kryssCache.indices) {
                    val kryss = kryssCache[i]
                    sheet.addCell(jxl.write.Label(0, i+1, kamererer[kryss.kamerer].name))
                    sheet.addCell(jxl.write.DateTime(1, i+1, Date(kryss.time)))
                    sheet.addCell(jxl.write.Label(2, i+1, KryssType.types[kryss.type].name))
                    sheet.addCell(jxl.write.Number(3, i+1, kryss.count.toDouble()))
                }
            }
            workbook.write()
            workbook.close()
            toast("Wrote data to ${file.getAbsolutePath()}")

            OutputStreamWriter(FileOutputStream(File(dir, "$date-raw.csv"))).use { writer ->
                writer.write("id, device, type, count, time, kamerer, replaces_id, replaces_device\n")
                database.use {
                    for (kryss in select("Kryss", *Kryss.selectList).parseList(Kryss.parser)) {
                        writer.write("${kryss.id}, ${kryss.device}, ${kryss.type}, ${kryss.count}, ${kryss.time}, ${kryss.kamerer}, ${kryss.replaces_id}, ${kryss.replaces_device}\n")
                    }
                }
            }

            /*val readableFile = File(dir, "$date-readable.csv")
            OutputStreamWriter(FileOutputStream(File(dir, "$date-readable.csv"))).use { writer ->
                writer.write("kamerer, time, type, count\n")
                database.use {
                    for (kryss in select("Kryss k", *SmallKryss.selectList).where("not exists (select * from Kryss r where r.replaces_id = ifnull(k.real_id, k._id) and r.replaces_device = k.device)").parseList(SmallKryss.parser)) {
                        writer.write("${kamererer[kryss.kamerer].name}, ${Date(kryss.time)}, ${KryssType.types[kryss.type].name}, ${kryss.count}\n")
                    }
                }
            }*/
        }

        return super<Activity>.onOptionsItemSelected(item)
    }

    private fun connectToDevice(d: WifiP2pDevice) {
        val config = WifiP2pConfig();
        config.deviceAddress = d.deviceAddress;
        receiver?.manager?.connect(receiver?.channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                toast("Connected to ${d.deviceAddress}")
            }

            override fun onFailure(reason: Int) {
                toast("Failed to connect: $reason")
            }
        })
    }

    fun updateKryssLists(reload:Boolean) {
        if(reload)
            loadKryss()
        else
            calculateKryss()

        val fragment = getFragmentManager().findFragmentById(android.R.id.content)
        info("updateKryssLists: $reload, $fragment")
        if(fragment is KamererListFragment) {
            fragment.updateData()
        } else if(fragment is KamererFragment) {
            fragment.updateData()
        }
    }

    private var searchingForPeers: Boolean = false

    fun peersReceived(peers: List<WifiP2pDevice>) {
        if(searchingForPeers) {
            searchingForPeers = false
            if (peers.size() >= 0) {
                selector("Which peer?", peers.map { d -> d.deviceName }.toArrayList(), { i ->
                    connectToDevice(peers[i])
                })
            } else {
                toast("No peers found")
            }
        }
    }

    private var wifiP2pInfo: WifiP2pInfo? = null
    fun connected(info: WifiP2pInfo) {
        if(info.groupFormed){
            toast("Connected")
            if(!info.isGroupOwner) {
                if(syncing.availablePermits() > 0)
                    startClient(info.groupOwnerAddress.getHostAddress())
            }
        } else if(wifiP2pInfo?.groupFormed == true) {
            toast("Disconnected")
        }
        this.wifiP2pInfo = info
        updateMenu()
    }

    fun updateMenu() {
        val wifiP2pInfo = wifiP2pInfo
        if(wifiP2pInfo != null) {
            menu?.findItem(R.id.action_connect)?.setTitle(if (wifiP2pInfo.groupFormed) "Koppla från" else "Anslut")
            menu?.findItem(R.id.action_sync)?.setVisible(wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner)
            if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
                menu?.findItem(R.id.action_sync)?.setTitle("Synka ${wifiP2pInfo.groupOwnerAddress.getHostAddress()}")
            }
        }
    }
}

data class KryssType(val name:String, val description:String, val color:Int, val alcohol:Double) {
    companion object {
        val types = arrayOf(
                KryssType("Svag", "4 cl 17% sprit", 0xff8e9103.toInt(), 0.17),
                KryssType("Vanlig", "4 cl 40% sprit\n1 burk öl/cider\n~2 dl vin", 0xff906500.toInt(), 0.4),
                KryssType("Fin", "4 cl 40% finsprit", 0xff051e76.toInt(), 0.4),
                KryssType("Wiskey", "4 cl wiskey", 0xff962000.toInt(), 0.4)
        )
    }
}
