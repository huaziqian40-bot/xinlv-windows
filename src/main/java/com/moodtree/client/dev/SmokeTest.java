/* 开发用冒烟测试：对真实服务器跑一遍"登录→离线记录→同步→拉取→墓碑删除→目录缓存"全流程。
 * 运行方式见 package.bat 注释或：
 *   set MOODTREE_HOME=<临时目录>
 *   java --module-path ... -m com.moodtree.client/com.moodtree.client.dev.SmokeTest
 * 依赖服务器上存在账号 _clienttest / ClientTest123（用 manage.py shell 创建）。 */
package com.moodtree.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.moodtree.client.Config;
import com.moodtree.client.api.ApiClient;
import com.moodtree.client.db.LocalDb;
import com.moodtree.client.model.MoodEntry;
import com.moodtree.client.sync.SyncEngine;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class SmokeTest {

    static int passed = 0, failed = 0;

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("PASS  " + name); }
        else { failed++; System.out.println("FAIL  " + name); }
    }

    public static void main(String[] args) throws Exception {
        Config config = new Config();
        config.setServerBase(System.getenv().getOrDefault("MOODTREE_SERVER", "http://127.0.0.1:8000"));
        ApiClient api = new ApiClient(config);
        LocalDb db = new LocalDb(config.dbPath());
        SyncEngine sync = new SyncEngine(config, api, db);

        // 1. 探活
        check("ping 服务器在线", api.ping());

        // 2. 登录
        JsonObject login = api.login("_clienttest", "ClientTest123");
        config.setToken(login.get("token").getAsString());
        config.setUsername(login.get("username").getAsString());
        config.save();
        check("登录拿到 token", !config.token().isEmpty());

        // 3. 本地离线记录一条 → 同步推送
        MoodEntry mine = MoodEntry.create(LocalDate.now(), "calm", "冒烟测试记录");
        db.saveLocal(mine);
        SyncEngine.SyncResult r1 = sync.sync();
        check("推送本地记录（pushed=1）", r1.error == null && r1.pushed == 1);
        check("推送后去掉脏标记", !db.get(mine.uuid).dirty);

        // 4. 模拟另一台设备直接在服务器上写一条 → 同步拉取
        MoodEntry other = MoodEntry.create(LocalDate.now(), "happy", "另一台设备的记录");
        other.uuid = UUID.randomUUID().toString();
        other.dirty = false;
        JsonArray arr = new JsonArray();
        arr.add(other.toJson());
        JsonObject payload = new JsonObject();
        payload.add("entries", arr);
        api.pushEntries(payload);
        SyncEngine.SyncResult r2 = sync.sync();
        check("拉取到另一设备的记录（pulled=1）", r2.error == null && r2.pulled == 1);
        MoodEntry got = db.get(other.uuid);
        check("拉取的记录内容正确", got != null && got.mood.equals("happy"));

        // 5. 本地删除（墓碑）→ 同步 → 服务器也是墓碑
        mine.deleted = true;
        mine.touchLocal();
        db.saveLocal(mine);
        SyncEngine.SyncResult r3 = sync.sync();
        check("推送墓碑（pushed=1）", r3.error == null && r3.pushed == 1);
        JsonObject pull = api.pullEntries(null);
        boolean tombOnServer = false;
        for (var el : pull.getAsJsonArray("entries")) {
            JsonObject o = el.getAsJsonObject();
            if (o.get("uuid").getAsString().equals(mine.uuid)) {
                tombOnServer = o.get("deleted").getAsBoolean();
            }
        }
        check("服务器上已是墓碑", tombOnServer);

        // 6. 更新冲突：服务端时间戳更新者赢（直接改服务器端，本地旧的不覆盖）
        MoodEntry newer = db.get(other.uuid);
        newer.note = "服务器端改的新内容";
        newer.updatedAt = OffsetDateTime.now().plusMinutes(5);   // 模拟服务器端更新更晚
        JsonArray arr2 = new JsonArray();
        arr2.add(newer.toJson());
        JsonObject p2 = new JsonObject();
        p2.add("entries", arr2);
        api.pushEntries(p2);
        // 本地把它改脏但用"过去的时间戳"再同步（模拟离线时改的旧内容），
        // 服务器较新的内容应该拉回来覆盖本地
        MoodEntry stale = db.get(other.uuid);
        stale.note = "本地旧内容";
        stale.updatedAt = OffsetDateTime.now().minusMinutes(1);   // 故意比服务器旧
        stale.dirty = true;
        db.saveLocal(stale);
        sync.sync();
        check("LWW：服务器较新者赢", "服务器端改的新内容".equals(db.get(other.uuid).note));

        // 7. 目录缓存
        check("推荐目录缓存刷新", sync.refreshCatalog());

        // 8. 个人数据
        JsonObject prof = api.profile();
        check("profile 返回用户名", "_clienttest".equals(prof.get("username").getAsString()));

        System.out.println("\n结果: " + passed + " 通过, " + failed + " 失败");
        System.exit(failed == 0 ? 0 : 1);
    }
}
