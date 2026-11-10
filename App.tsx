import React, {useEffect, useState, useCallback} from 'react';
import {View, Text, Pressable, FlatList, Switch, TextInput, Linking, StyleSheet, NativeModules, StatusBar, PermissionsAndroid, Platform, Alert} from 'react-native';

const S = NativeModules.SpamShield;
const C = {bg: '#0D0F1A', card: '#171A2B', line: '#262A42', text: '#F2F3FA', dim: '#8A8FB0', accent: '#8B7CFF', bad: '#FF5C7A', ok: '#4ADE9B'};
const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#'];
const SUB: Record<string, string> = {'2': 'ABC', '3': 'DEF', '4': 'GHI', '5': 'JKL', '6': 'MNO', '7': 'PQRS', '8': 'TUV', '9': 'WXYZ'};
type Tab = 'dial' | 'shield' | 'log';

export default function App() {
  const [tab, setTab] = useState<Tab>('dial');
  return (
    <View style={s.root}>
      <StatusBar barStyle="light-content" backgroundColor={C.bg} />
      <View style={{flex: 1}}>{tab === 'dial' ? <Dialer /> : tab === 'shield' ? <Shield /> : <Log />}</View>
      <View style={s.tabs}>
        {([['dial', 'Keypad'], ['shield', 'Shield'], ['log', 'Blocked']] as [Tab, string][]).map(([k, l]) => (
          <Pressable key={k} onPress={() => setTab(k)} style={[s.tab, tab === k && s.tabOn]}>
            <Text style={[s.tabTxt, tab === k && {color: C.text}]}>{l}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

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
    <View style={s.screen}>
      <View style={s.display}>
        <Text style={s.num} numberOfLines={1} adjustsFontSizeToFit>{num || 'Enter number'}</Text>
      </View>
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
        <Pressable style={s.del} onPress={() => setNum(n => n.slice(0, -1))} onLongPress={() => setNum('')}>
          <Text style={{color: C.dim, fontSize: 22}}>⌫</Text>
        </Pressable>
      </View>
    </View>
  );
}

function Shield() {
  const [list, setList] = useState<string[]>([]);
  const [unknown, setUnknown] = useState(false);
  const [input, setInput] = useState('');
  const [active, setActive] = useState(false);
  const load = useCallback(async () => {
    setList(await S.getBlocklist());
    setUnknown(await S.getBlockUnknown());
    setActive(await S.isScreeningActive());
  }, []);
  useEffect(() => { load(); }, [load]);
  const add = async () => { if (!input.trim()) return; await S.addBlocked(input.trim()); setInput(''); load(); };
  return (
    <View style={s.screen}>
      <Text style={s.h1}>Shield</Text>
      <View style={[s.card, {borderColor: active ? C.ok : C.bad}]}>
        <Text style={s.cardT}>{active ? 'Spam blocking is on' : 'Spam blocking is off'}</Text>
        <Text style={s.dim}>{active ? 'Blocked numbers are rejected before your phone rings.' : 'Allow call screening so this app can reject spam calls.'}</Text>
        {!active && <Pressable style={s.btn} onPress={async () => { await S.requestScreeningRole(); setTimeout(load, 1500); }}><Text style={s.btnT}>Turn on blocking</Text></Pressable>}
      </View>
      <View style={s.cardRow}>
        <View style={{flex: 1}}><Text style={s.cardT}>Block unknown callers</Text><Text style={s.dim}>Calls with hidden numbers</Text></View>
        <Switch value={unknown} trackColor={{true: C.accent}} onValueChange={async v => { setUnknown(v); await S.setBlockUnknown(v); }} />
      </View>
      <Text style={s.dim}>Add a number, or a prefix ending in * (for example +9140*)</Text>
      <View style={s.addRow}>
        <TextInput style={s.input} value={input} onChangeText={setInput} placeholder="Number or prefix*" placeholderTextColor={C.dim} keyboardType="phone-pad" />
        <Pressable style={s.btnSm} onPress={add}><Text style={s.btnT}>Block</Text></Pressable>
      </View>
      <FlatList data={list} keyExtractor={i => i} ListEmptyComponent={<Text style={s.dim}>No blocked numbers yet.</Text>}
        renderItem={({item}) => (
          <View style={s.item}><Text style={s.itemT}>{item}</Text>
            <Pressable onPress={async () => { await S.removeBlocked(item); load(); }}><Text style={{color: C.bad}}>Remove</Text></Pressable></View>
        )} />
    </View>
  );
}

function Log() {
  const [log, setLog] = useState<string[]>([]);
  useEffect(() => { S.getLog().then(setLog); }, []);
  return (
    <View style={s.screen}>
      <Text style={s.h1}>Blocked calls</Text>
      <FlatList data={log} keyExtractor={(_, i) => String(i)} ListEmptyComponent={<Text style={s.dim}>Nothing blocked yet. Blocked calls will show up here.</Text>}
        renderItem={({item}) => { const [t, n] = item.split('|'); return (
          <View style={s.item}><View><Text style={s.itemT}>{n || 'Unknown number'}</Text><Text style={s.dim}>{new Date(Number(t)).toLocaleString()}</Text></View>
            <Text style={{color: C.bad}}>Spam</Text></View>); }} />
    </View>
  );
}

const s = StyleSheet.create({
  root: {flex: 1, backgroundColor: C.bg}, screen: {flex: 1, padding: 20, paddingTop: 48},
  tabs: {flexDirection: 'row', margin: 14, padding: 5, backgroundColor: C.card, borderRadius: 22},
  tab: {flex: 1, paddingVertical: 12, alignItems: 'center', borderRadius: 18}, tabOn: {backgroundColor: C.line},
  tabTxt: {color: C.dim, fontWeight: '600'},
  display: {height: 90, justifyContent: 'center', alignItems: 'center'}, num: {color: C.text, fontSize: 40, fontWeight: '300', letterSpacing: 2},
  pad: {flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 14},
  key: {width: 82, height: 82, borderRadius: 41, backgroundColor: C.card, alignItems: 'center', justifyContent: 'center'},
  keyTxt: {color: C.text, fontSize: 28, fontWeight: '500'}, keySub: {color: C.dim, fontSize: 9, letterSpacing: 1},
  row: {flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 20, marginTop: 18},
  callBtn: {width: 72, height: 72, borderRadius: 36, backgroundColor: C.ok, alignItems: 'center', justifyContent: 'center'}, callTxt: {color: '#06130C', fontWeight: '800'},
  del: {width: 72, alignItems: 'center'},
  h1: {color: C.text, fontSize: 30, fontWeight: '700', marginBottom: 16},
  card: {backgroundColor: C.card, borderRadius: 20, padding: 18, marginBottom: 12, borderWidth: 1}, cardRow: {backgroundColor: C.card, borderRadius: 20, padding: 18, marginBottom: 16, flexDirection: 'row', alignItems: 'center'},
  cardT: {color: C.text, fontSize: 17, fontWeight: '600', marginBottom: 4}, dim: {color: C.dim, fontSize: 13, marginBottom: 8},
  btn: {backgroundColor: C.accent, padding: 14, borderRadius: 14, alignItems: 'center', marginTop: 8}, btnSm: {backgroundColor: C.accent, paddingHorizontal: 20, justifyContent: 'center', borderRadius: 14}, btnT: {color: '#fff', fontWeight: '700'},
  addRow: {flexDirection: 'row', gap: 10, marginBottom: 12}, input: {flex: 1, backgroundColor: C.card, color: C.text, borderRadius: 14, paddingHorizontal: 16, height: 50},
  item: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: C.line}, itemT: {color: C.text, fontSize: 16},
});
