package odc;

import java.util.*;
import java.util.stream.Collectors;

/** One canonical record, anchored to a physical rack/controller, never copied on topology changes. */
final class EventJournal {
    enum Level { INFO, SUCCESS, WARNING, ERROR, CRITICAL }
    enum Category { SYSTEM, HARDWARE, POOL, TASK, RESOURCE, NETWORK, POWER, FINANCE, COOLING }
    enum Target { NONE, SERVER, TASK, POOL, RACK }
    static final int PAYMENT_WINDOW=60;
    static final Comparator<Entry> NEWEST=Comparator.comparingLong((Entry e)->e.last).thenComparingLong(e->e.sequence).reversed();
    static final class Entry {
        final UUID id,owner,source; UUID scope; UUID origin; final long first,sequence; long last,amount; int repeats=1;
        final Level level; final Category category; final Target target; final String code,message; final int currency;
        Entry(UUID id,UUID owner,UUID scope,UUID source,long first,long seq,Level level,Category category,Target target,String code,String message,int currency,long amount){this.id=id;this.owner=owner;this.scope=scope;this.origin=scope;this.source=source;this.first=first;this.last=first;this.sequence=seq;this.level=level;this.category=category;this.target=target;this.code=code;this.message=message;this.currency=currency;this.amount=amount;}
        boolean matches(UUID o,UUID s,UUID src,Level l,Category c,Target t,String k,String m,int coin){return owner.equals(o)&&scope.equals(s)&&Objects.equals(source,src)&&level==l&&category==c&&target==t&&code.equals(k)&&message.equals(m)&&currency==coin;}
    }
    private final Map<UUID,List<Entry>> records=new HashMap<>(); private long sequence; private int limit;
    EventJournal(int limit){this.limit=Math.max(1,limit);}
    int limit(){return limit;}
    void clear(){records.clear();sequence=0;}
    void restore(Entry e){records.computeIfAbsent(e.scope,k->new ArrayList<>()).add(e);sequence=Math.max(sequence,e.sequence);}
    List<Entry> all(){List<Entry> out=new ArrayList<>();for(List<Entry> list:records.values())out.addAll(list);out.sort(NEWEST);return out;}
    void configure(int n){limit=Math.max(1,n);for(List<Entry> l:records.values())trim(l);}
    private void trim(List<Entry> l){l.sort(NEWEST);if(l.size()>limit)l.subList(limit,l.size()).clear();}
    Entry add(long tick,UUID owner,UUID scope,UUID source,Level level,Category category,Target target,String code,String message,int currency,long amount){
        if(owner==null||scope==null)return null;message=message.substring(0,Math.min(240,message.length()));List<Entry> l=records.computeIfAbsent(scope,k->new ArrayList<>());
        boolean payment=currency>=0&&"payment".equals(code), repeat=level.ordinal()>=Level.WARNING.ordinal()&&currency<0;
        if(payment||repeat)for(Entry e:l)if(e.matches(owner,scope,source,level,category,target,code,message,currency)&&tick>=e.last&&(payment?tick/PAYMENT_WINDOW==e.first/PAYMENT_WINDOW:tick-e.last<=60)){
            e.last=tick;e.repeats=(int)Math.min(Integer.MAX_VALUE,(long)e.repeats+1);e.amount=saturate(e.amount,amount);trim(l);return e;
        }
        Entry e=new Entry(UUID.randomUUID(),owner,scope,source,tick,++sequence,level,category,target,code,message,currency,amount);l.add(e);trim(l);return e;
    }
    void move(UUID owner,UUID from,UUID to){if(owner==null||from==null||to==null||from.equals(to))return;List<Entry> old=records.get(from);if(old==null)return;List<Entry> dest=records.computeIfAbsent(to,k->new ArrayList<>());Iterator<Entry> it=old.iterator();while(it.hasNext()){Entry e=it.next();if(e.owner.equals(owner)){it.remove();e.scope=to;dest.add(e);}}if(old.isEmpty())records.remove(from);trim(dest);}
    static long saturate(long a,long b){if(b>0&&a>Long.MAX_VALUE-b)return Long.MAX_VALUE;if(b<0&&a<Long.MIN_VALUE-b)return Long.MIN_VALUE;return a+b;}
    List<Entry> recent(UUID owner,Collection<UUID> scopes){List<Entry> out=new ArrayList<>();for(UUID scope:new HashSet<>(scopes))for(Entry e:records.getOrDefault(scope,Collections.emptyList()))if(e.owner.equals(owner))out.add(e);out.sort(NEWEST);return new ArrayList<>(out.subList(0,Math.min(limit,out.size())));}
    // Trim the union once a fully observed network is known; never duplicate records across anchors.
    void prune(UUID owner,Collection<UUID> scopes){Set<UUID> keep=recent(owner,scopes).stream().map(e->e.id).collect(Collectors.toSet());for(UUID scope:scopes){List<Entry> l=records.get(scope);if(l!=null){l.removeIf(e->e.owner.equals(owner)&&!keep.contains(e.id));if(l.isEmpty())records.remove(scope);}}}
    static List<Entry> filter(List<Entry> entries,String search,String category,String level,boolean oldest){return filter(entries,search,category,level,oldest,e->"");}
    static List<Entry> filter(List<Entry> entries,String search,String category,String level,boolean oldest,java.util.function.Function<Entry,String> label){String q=search.toLowerCase(Locale.ROOT);return entries.stream().filter(e->(category.isEmpty()||e.category.name().equals(category))&&(level.isEmpty()||e.level.name().equals(level))&&(q.isEmpty()||(e.message+" "+e.code+" "+e.category+" "+e.level+" "+e.target+" "+e.source+" "+label.apply(e)).toLowerCase(Locale.ROOT).contains(q))).sorted(oldest?NEWEST.reversed():NEWEST).collect(Collectors.toList());}
    static final class Query {final String search,category,level;final boolean oldest;Query(String s,String c,String l,boolean o){search=s==null?"":s.replace('|',' ').substring(0,Math.min(64,s.length()));category=c==null?"":c;level=l==null?"":l;oldest=o;}String encode(){return category+"|"+level+"|"+(oldest?1:0)+"|"+search;}static Query decode(String s){String[] a=s==null?new String[0]:s.split("\\|",4);if(a.length!=4)return new Query("","","",false);String c=a[0],l=a[1];try{if(!c.isEmpty())Category.valueOf(c);if(!l.isEmpty())Level.valueOf(l);}catch(IllegalArgumentException ex){return new Query("","","",false);}return new Query(a[3],c,l,"1".equals(a[2]));}}
}
