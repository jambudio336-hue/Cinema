const BASE = (process.env.DRACIN_API_BASE || "https://api.dramabuzz.sbs").replace(/\/$/, "");
const PROVIDERS = new Set(["starshort","dramabite","freereels","fundrama","microdrama","vigloo","dramabox","dramawave","netshort","idrama","shortmax","goodshort","melolo","velolo","reelshort","flickreels","stardusttv","serialplus","dotdrama","rapidtv","shortswave","dramanova","cubetv","reelbuzz","flareflow","moboreels","happyshort","reelife","pinedrama","flextv","reelala","anyreel","raptdrama","bonustv","minitv","golddrama","joyreels","kalostv","vibeshort","bilitv","iqiyi","bstation"]);
const BASE_PATH={idrama:"/idrama",flickreels:"/flickreels/api/flickreels"};
const counters=new Map();
function send(res,status,body){res.statusCode=status;res.setHeader("Content-Type","application/json; charset=utf-8");res.end(JSON.stringify(body));}
function rate(ip){const now=Date.now(),v=counters.get(ip);if(!v||now-v.t>60000){counters.set(ip,{t:now,n:1});return true;}return ++v.n<=50;}
module.exports=async(req,res)=>{
 res.setHeader("Access-Control-Allow-Origin","*");res.setHeader("Access-Control-Allow-Methods","GET, OPTIONS");res.setHeader("Access-Control-Allow-Headers","Content-Type");res.setHeader("Cache-Control","no-store");
 if(req.method==="OPTIONS"){res.statusCode=204;return res.end();} if(req.method!=="GET")return send(res,405,{error:"method_not_allowed"});
 const ip=String(req.headers["x-forwarded-for"]||"unknown").split(",")[0].trim();if(!rate(ip))return send(res,429,{error:"proxy_rate_limited"});
 const key=process.env.DRACIN_API_KEY;if(!key)return send(res,503,{error:"api_key_not_configured",message:"DRACIN_API_KEY is not configured on the API server."});
 const q=req.query||{},provider=String(q.provider||"starshort").toLowerCase(),action=String(q.action||"");
 if(!PROVIDERS.has(provider))return send(res,400,{error:"unsupported_provider"});
 const prefix=BASE_PATH[provider]||"/"+provider+"/api/v1",id=String(q.id||""),ep=String(q.ep||"1");
 const params=new URLSearchParams({key,lang:String(q.lang||"id")});let path="";
 if(action==="status"){path="/api/status";params.delete("lang");}
 else if(action==="search"){const term=String(q.q||"").trim();if(!term||term.length>120)return send(res,400,{error:"invalid_query"});path=prefix+"/search";params.set(provider==="dramabox"?"keyword":"q",term);if(q.page)params.set("page",String(q.page));}
 else if(action==="feed"){const feeds={starshort:"/trending",dramabox:"/discover",shortmax:"/home",reelshort:"/featured",dramabite:"/recommend",goodshort:"/toppicks",reelbuzz:"/buzz",flickreels:"/trending"};path=prefix+(feeds[provider]||"/trending");if(q.page)params.set("page",String(q.page));}
 else if(action==="detail"){if(!id||id.length>180)return send(res,400,{error:"invalid_id"});path=provider==="idrama"?prefix+"/drama/"+encodeURIComponent(id):provider==="flickreels"?prefix+"/detail":prefix+"/detail/"+encodeURIComponent(id);if(provider==="flickreels")params.set("id",id);}
 else if(action==="episodes"){if(!id||id.length>180)return send(res,400,{error:"invalid_id"});path=provider==="idrama"?prefix+"/episodes/"+encodeURIComponent(id):provider==="flickreels"?prefix+"/allepisode":prefix+"/episodes/"+encodeURIComponent(id);if(provider==="flickreels")params.set("id",id);}
 else if(action==="stream"){if(!id||id.length>180||!/^\d{1,5}$/.test(ep))return send(res,400,{error:"invalid_episode"});if(provider==="dramabox")path=prefix+"/download/"+encodeURIComponent(id);else if(provider==="shortmax")path=prefix+"/play/"+encodeURIComponent(id);else if(provider==="flickreels"){path=prefix+"/episode";params.set("id",id);params.set("ep",ep);}else if(provider==="idrama")path=prefix+"/unlock/"+encodeURIComponent(id)+"/"+ep;else path=prefix+"/play/"+encodeURIComponent(id)+"/"+ep;}
 else return send(res,400,{error:"invalid_action"});
 try{const upstream=await fetch(BASE+path+"?"+params.toString(),{headers:{Accept:"application/json"},signal:AbortSignal.timeout(20000)});const raw=await upstream.text();let body;try{body=JSON.parse(raw);}catch{body={raw:raw.slice(0,2000)};}return send(res,upstream.status,body);}
 catch(e){return send(res,502,{error:"upstream_unreachable",message:String(e&&e.message||e)});}
};