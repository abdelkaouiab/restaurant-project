import { createContext, useCallback, useContext, useEffect, useMemo, useState, type MouseEvent, type ReactNode } from 'react';

interface RouterValue { chemin: string; naviguer: (chemin: string, remplacer?: boolean) => void; }
const RouterContext=createContext<RouterValue|null>(null);

export function RouterProvider({children,initialPath}:{children:ReactNode;initialPath?:string}){
 const [chemin,setChemin]=useState(initialPath??window.location.pathname);
 useEffect(()=>{if(initialPath)return;const changer=()=>setChemin(window.location.pathname);window.addEventListener('popstate',changer);return()=>window.removeEventListener('popstate',changer);},[initialPath]);
 const naviguer=useCallback((destination:string,remplacer=false)=>{if(initialPath){setChemin(destination);return;}window.history[remplacer?'replaceState':'pushState']({},'',destination);setChemin(destination);},[initialPath]);
 return <RouterContext.Provider value={useMemo(()=>({chemin,naviguer}),[chemin,naviguer])}>{children}</RouterContext.Provider>;
}
export function useRouter(){const r=useContext(RouterContext);if(!r)throw new Error('Routeur absent.');return r;}
export function Link({to,children,className}:{to:string;children:ReactNode;className?:string}){const {naviguer}=useRouter();const cliquer=(e:MouseEvent<HTMLAnchorElement>)=>{if(!e.defaultPrevented&&e.button===0&&!e.metaKey&&!e.ctrlKey&&!e.shiftKey&&!e.altKey){e.preventDefault();naviguer(to);}};return <a href={to} className={className} onClick={cliquer}>{children}</a>;}
export function Navigate({to}:{to:string}){const {naviguer}=useRouter();useEffect(()=>naviguer(to,true),[naviguer,to]);return null;}
