import React, {useEffect, useState, useCallback} from 'react';
import {View, Text, Pressable, FlatList, Switch, TextInput, Linking, StyleSheet, NativeModules, StatusBar, PermissionsAndroid, Platform, Alert, ScrollView} from 'react-native';

const S = NativeModules.SpamShield;
const C = {bg: '#0A0B14', card: '#14162A', card2: '#1B1E38', line: '#262A4A', text: '#F4F5FF', dim: '#8B90B8', accent: '#7C6CFF', ok: '#3DDC97', bad: '#FF4D6D'};
const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#'];
const SUB: Record<string, string> = {'2': 'ABC', '3': 'DEF', '4': 'GHI', '5': 'JKL', '6': 'MNO', '7': 'PQRS', '8': 'TUV', '9': 'WXYZ'};
type Tab = 'home' | 'dial' | 'rules';

const ago = (t: number) => {
  const m = Math.floor((Date.now() - t) / 60000);
  if (m < 1) return 'Just now';
  if (m < 60) return m + ' min ago';
  const h = Math.floor(m / 60);
  if (h < 24) return h + ' h ago';
  return new Date(t).toLocaleDateString();
};

export default function App() {
  const [tab, setTab] = useState<Tab>('home');
  const items: [Tab, string, string][] = [['home', '◉', 'Dashboard'], ['dial', '⌗', 'Keypad'], ['rules', '⛨', 'Rules']];
  return (
    <View style={s.root}>
      <StatusBar barStyle="light-content" backgroundColor={C.bg} />
      <View style={{flex: 1}}>{tab === 'home' ? <Home go={setTab} /> : tab === 'dial' ? <Dialer /> : <Rules />}</View>
      <View style={s.tabs}>
        {items.map(([k, i, l]) => (
          <Pressable key={k} onPress={() => setTab(k)} style={s.tab}>
            <Text style={[s.tabI, tab === k && {color: C.accent}]}>{i}</Text>
            <Text style={[s.tabT, tab === k && {color: C.text}]}>{l}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

function Home({go}: {go: (t: Tab) => void}) {
  const [log, setLog] = useState<string[]>([]);
  const [rules, setRules] = useState(0);
  const [unknown, setUnknown] = useState(false);
  const [active, setActive] = useState(false);
  const load = useCallback(async () => {
    setLog(await S.getLog());
    setRules((await S.getBlocklist()).length);
    setUnknown(await S.getBlockUnknown());
    setActive(await S.isScreeningActive());
  }, []);
  useEffect(() => { load(); const i = setInterval(load, 4000); return () => clearInterval(i); }, [load]);
  const day = new Date(); day.setHours(0, 0, 0, 0);
  const rows = log.map(l => { const [t, n] = l.split('|'); return {t: Number(t), n}; });
  const today = rows.filter(r => r.t >= day.getTime()).length;
  const col = active ? C.ok : C.bad;
  return (
    <ScrollView contentContainerStyle={s.screen}>
      <Text style={s.brand}>SpamShield</Text>
      <View style={s.hero}>
        <View style={[s.ring3, {borderColor: col + '22'}]}>
          <View style={[s.ring2, {borderColor: col + '55'}]}>
            <View style={[s.ring1, {borderColor: col}]}>
              <Text style={s.big}>{today}</Text>
            </View>
          </View>
        </View>
        <Text style={s.heroT}>spam calls blocked today</Text>
        <View style={[s.pill, {backgroundColor: col + '22'}]}>
          <View style={[s.dot, {backgroundColor: col}]} />
          <Text style={{color: col, fontWeight: '700'}}>{active ? 'Protection on' : 'Protection off'}</Text>
        </View>
      </View>
      {!active && (
        <Pressable style={s.warn} onPress={async () => { await S.requestScreeningRole(); setTimeout(load, 1500); }}>
          <Text style={s.warnT}>Blocking is off. Tap to allow call screening.</Text>
        </Pressable>
      )}
      <View style={s.stats}>
        <Stat v={String(rows.length)} l="Total blocked" />
        <Stat v={String(rules)} l="Active rules" onPress={() => go('rules')} />
        <Stat v={unknown ? 'On' : 'Off'} l="Hidden numbers" onPress={() => go('rules')} />
      </View>
      <View style={s.rowBetween}>
        <Text style={s.h2}>Recent activity</Text>
        {rows.length > 0 && <Pressable onPress={async () => { await S.clearLog(); load(); }}><Text style={{color: C.dim}}>Clear</Text></Pressable>}
      </View>
      {rows.length === 0 ? (
        <View style={s.empty}><Text style={s.emptyT}>No blocked calls yet</Text><Text style={s.dim}>Add numbers in Rules. Blocked calls will appear here.</Text></View>
      ) : rows.slice(0, 50).map((r, i) => (
        <View key={i} style={s.item}>
          <View style={s.avatar}><Text style={{color: C.bad, fontWeight: '800'}}>!</Text></View>
          <View style={{flex: 1}}><Text style={s.itemT}>{r.n || 'Hidden number'}</Text><Text style={s.dim}>{ago(r.t)}</Text></View>
          <Text style={s.tag}>Blocked</Text>
        </View>
      ))}
    </ScrollView>
  );
}

const Stat = ({v, l, onPress}: {v: string; l: string; onPress?: () => void}) => (
  <Pressable style={s.stat} onPress={onPress}><Text style={s.statV}>{v}</Text><Text style={s.dim}>{l}</Text></Pressable>
);

function Dialer() {
  const [num, setNum] = useState('');
  const call = async () => {
    if (!num) return;
    if (Platform.OS === 'android') {
      const r = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.CALL_PHONE);
      if (r !== 'granted') return Alert.alert('Permission needed', 'Allow calling to place calls from this app.');
    }
    Linking.openURL('tel:' + encodeURIComponent(num));
  };
  return (
    <View style={[s.screen, {flex: 1}]}>
      <View style={s.display}><Text style={s.num} numberOfLines={1} adjustsFontSizeToFit>{num || ' '}</Text></View>
      <View style={s.pad}>
        {KEYS.map(k => (
          <Pressable key={k} style={({pressed}) => [s.key, pressed && {backgroundColor: C.accent}]} onPress={() => setNum(n => n + k)}>
            <Text style={s.keyTxt}>{k}</Text>
            {SUB[k] ? <Text style={s.keySub}>{SUB[k]}</Text> : null}
          </Pressable>
        ))}
      </View>
      <View style={s.row}>
        <View style={{width: 72}} />
        <Pressable style={s.callBtn} onPress={call}><Text style={s.callTxt}>Call</Text></Pressable>
        <Pressable style={s.del} onPress={() => setNum(n => n.slice(0, -1))} onLongPress={() => setNum('')}><Text style={{color: C.dim, fontSize: 24}}>⌫</Text></Pressable>
      </View>
    </View>
  );
}

function Rules() {
  const [list, setList] = useState<string[]>([]);
  const [unknown, setUnknown] = useState(false);
  const [input, setInput] = useState('');
  const load = useCallback(async () => { setList(await S.getBlocklist()); setUnknown(await S.getBlockUnknown()); }, []);
  useEffect(() => { load(); }, [load]);
  const add = async () => { if (!input.trim()) return; await S.addBlocked(input.trim()); setInput(''); load(); };
  return (
    <ScrollView contentContainerStyle={s.screen} keyboardShouldPersistTaps="handled">
      <Text style={s.h1}>Rules</Text>
      <View style={s.cardRow}>
        <View style={{flex: 1}}><Text style={s.cardT}>Block hidden numbers</Text><Text style={s.dim}>Calls that show no caller ID</Text></View>
        <Switch value={unknown} trackColor={{true: C.accent, false: C.line}} onValueChange={async v => { setUnknown(v); await S.setBlockUnknown(v); }} />
      </View>
      <Text style={s.h2}>Block a number or series</Text>
      <Text style={s.dim}>Use a full number, or end with * to block every number that starts the same way, like +9140*</Text>
      <View style={s.addRow}>
        <TextInput style={s.input} value={input} onChangeText={setInput} placeholder="Number or prefix*" placeholderTextColor={C.dim} keyboardType="phone-pad" />
        <Pressable style={s.btnSm} onPress={add}><Text style={s.btnT}>Block</Text></Pressable>
      </View>
      {list.length === 0 && <Text style={s.dim}>No rules yet.</Text>}
      {list.map(item => (
        <View key={item} style={s.item}>
          <Text style={[s.itemT, {flex: 1}]}>{item}</Text>
          <Pressable onPress={async () => { await S.removeBlocked(item); load(); }}><Text style={{color: C.bad}}>Remove</Text></Pressable>
        </View>
      ))}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: {flex: 1, backgroundColor: C.bg}, screen: {padding: 20, paddingTop: 52, paddingBottom: 30},
  tabs: {flexDirection: 'row', backgroundColor: C.card, borderTopWidth: 1, borderTopColor: C.line, paddingVertical: 8},
  tab: {flex: 1, alignItems: 'center', paddingVertical: 4}, tabI: {color: C.dim, fontSize: 20}, tabT: {color: C.dim, fontSize: 12, marginTop: 2, fontWeight: '600'},
  brand: {color: C.dim, fontSize: 15, fontWeight: '700', letterSpacing: 0.5, marginBottom: 8},
  hero: {backgroundColor: C.card, borderRadius: 28, paddingVertical: 28, alignItems: 'center', marginBottom: 14},
  ring3: {width: 190, height: 190, borderRadius: 95, borderWidth: 2, alignItems: 'center', justifyContent: 'center'},
  ring2: {width: 160, height: 160, borderRadius: 80, borderWidth: 2, alignItems: 'center', justifyContent: 'center'},
  ring1: {width: 126, height: 126, borderRadius: 63, borderWidth: 4, alignItems: 'center', justifyContent: 'center', backgroundColor: C.bg},
  big: {color: C.text, fontSize: 54, fontWeight: '800'}, heroT: {color: C.dim, fontSize: 15, marginTop: 16, marginBottom: 12},
  pill: {flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20}, dot: {width: 8, height: 8, borderRadius: 4},
  warn: {backgroundColor: C.bad + '22', borderColor: C.bad, borderWidth: 1, borderRadius: 16, padding: 14, marginBottom: 14}, warnT: {color: C.text, fontWeight: '600'},
  stats: {flexDirection: 'row', gap: 10, marginBottom: 22}, stat: {flex: 1, backgroundColor: C.card, borderRadius: 18, padding: 14},
  statV: {color: C.text, fontSize: 24, fontWeight: '800', marginBottom: 2},
  rowBetween: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10},
  h1: {color: C.text, fontSize: 30, fontWeight: '800', marginBottom: 18}, h2: {color: C.text, fontSize: 18, fontWeight: '700', marginBottom: 6},
  dim: {color: C.dim, fontSize: 13},
  item: {flexDirection: 'row', alignItems: 'center', backgroundColor: C.card, borderRadius: 16, padding: 14, marginBottom: 8, gap: 12}, itemT: {color: C.text, fontSize: 16, fontWeight: '600'},
  avatar: {width: 40, height: 40, borderRadius: 20, backgroundColor: C.bad + '22', alignItems: 'center', justifyContent: 'center'},
  tag: {color: C.bad, fontWeight: '700', fontSize: 12, backgroundColor: C.bad + '1A', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 10, overflow: 'hidden'},
  empty: {backgroundColor: C.card, borderRadius: 18, padding: 24, alignItems: 'center'}, emptyT: {color: C.text, fontSize: 16, fontWeight: '700', marginBottom: 4},
  display: {height: 90, justifyContent: 'center', alignItems: 'center'}, num: {color: C.text, fontSize: 40, fontWeight: '300', letterSpacing: 2},
  pad: {flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 14},
  key: {width: 82, height: 82, borderRadius: 41, backgroundColor: C.card, alignItems: 'center', justifyContent: 'center'},
  keyTxt: {color: C.text, fontSize: 28, fontWeight: '500'}, keySub: {color: C.dim, fontSize: 9, letterSpacing: 1},
  row: {flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 20, marginTop: 18},
  callBtn: {width: 72, height: 72, borderRadius: 36, backgroundColor: C.ok, alignItems: 'center', justifyContent: 'center'}, callTxt: {color: '#04170D', fontWeight: '800'}, del: {width: 72, alignItems: 'center'},
  cardRow: {backgroundColor: C.card, borderRadius: 18, padding: 18, marginBottom: 22, flexDirection: 'row', alignItems: 'center'}, cardT: {color: C.text, fontSize: 16, fontWeight: '700', marginBottom: 2},
  addRow: {flexDirection: 'row', gap: 10, marginVertical: 12}, input: {flex: 1, backgroundColor: C.card, color: C.text, borderRadius: 14, paddingHorizontal: 16, height: 50},
  btnSm: {backgroundColor: C.accent, paddingHorizontal: 22, justifyContent: 'center', borderRadius: 14}, btnT: {color: '#fff', fontWeight: '700'},
});
