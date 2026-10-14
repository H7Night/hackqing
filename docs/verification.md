# hackqing 真机验证与变更记录

## 2026-10-13 · Health Connect 同步/删除 + 后台服务启动修复

分支：`feat/health-connect-sync-delete`

- `f551a11` fix: 启动时按已保存开关自动拉起后台接收服务
- `fac4e10` feat: Health Connect 同步去重 + 删除记录时联动删除 HC 数据

### 变更要点

- **同步去重**：每条测量使用**稳定的 `clientRecordId`**（`hackqing-weight-<id>` / `hackqing-bodyfat-<id>`），
  重复同步同一记录在 Health Connect 中只更新、不新增；本地新增 `hcSynced` 标记，已同步的卡片显示「已同步到 Health Connect」。
- **联动删除**：删除**已同步**的记录时弹窗询问「仅删本地 / 同时删 HC」；确认后按 `clientRecordId`
  删除 HC 中对应的体重（WeightRecord）与体脂（BodyFatRecord）记录；HC 不可用/无权限时提示且仍删本地。
- **后台服务启动**：此前仅手动切换开关会启动 `ScaleService`，强停/被回收后重开 App 不会恢复；
  现在 `MainActivity` 在具备权限且 `bg_enabled` 为真时 `startForegroundService`，并给 `ScaleService.observe` 加了去重保护。

### 真机结果（OnePlus 15 / Android 16 / KernelSU）

- ✅ **启动自动起服务**：强停后重开 App，`ScaleService` 与常驻通知（id=1001）自动出现。
- ✅ **设备链路**：扫描发现 `YUNMAI-Scale-3 80:EA:CA:00:70:DF` → 自动连接 → `ffe4`/`ffe9` → MTU=247 →
  鉴权/用户信息/序列号/固件/电量握手完成。
- ✅ **后台接收**：App 切后台时上秤，收到 `69.05 kg / 体脂 18.8% / 阻抗 347 / 用户#1` 并持久化（历史 3 条）。
- ✅ **同步**：点同步后该条 `hcSynced=true`、卡片显示「已同步到 Health Connect」；再次同步不报错。
- ✅ **HC 端核对**（Health Connect → 資料和存取權 → 資料 → 體重）：可见 `22:20 hackqing 69 公斤`、`06:40 hackqing 66.7 公斤`。
- ⚠️ **一次性遗留**：上述时间点各有 **2 条**——旧版本同步的记录（无 `clientRecordId`）与本次新逻辑新增的记录并存。
  对 `69.05` 再次同步未产生第 3 条，说明新的去重逻辑生效；旧的无 ID 记录需在 Health Connect 中手动清理。
- ⏳ **未完成**：删除弹窗「同时删 HC」的端到端确认（测试中途 USB 断开，暂停）。
- ✅ 历史/详情/单项同步到 HC/调试页均正常。

### 待办

- [ ] 在真机上完成「删除已同步记录 → 同时删 HC」的端到端验证，并在 HC 中确认条目消失。
- [ ] 用全新测量（或清空 HC 后）确认「连续同步两次只留一条」。
- [ ] 在 Health Connect 中清理旧版遗留的重复条目。
