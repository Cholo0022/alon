import { useCallback, useState } from 'react'
import { LocalEntryPanel, type EntryField } from './Forms'
import { NewPurchaseApparelForm } from './NewPurchaseApparelForm'
import { apparelDraftKey, readApparelDraft, type ApparelDraft } from '../lib/apparelDraft'
import './article-picker.css'

export function CatalogArticleEntry({fields,duplicateNames,draftScope}:{fields:EntryField[];duplicateNames:string[];draftScope?:string}) {
  const draftKey=draftScope ? apparelDraftKey(draftScope,'catalog') : undefined
  const [draft,setDraft]=useState(()=>readApparelDraft(draftKey))
  const [kind,setKind]=useState('ARTICULO'),[version,setVersion]=useState(0),[busy,setBusy]=useState(false),[message,setMessage]=useState(''),[open,setOpen]=useState(false),[initialOpen,setInitialOpen]=useState(false)
  const draftChanged=useCallback((value:ApparelDraft|null)=>setDraft(value),[])
  return <>
    {draft&&!open&&<div className="notice"><span>Tenés una prenda sin terminar: {draft.common.name||'Sin nombre'}.</span><button type="button" className="secondary-button" onClick={()=>{setKind(draft.kind);setInitialOpen(true);setOpen(true);setVersion((value)=>value+1)}}>Continuar borrador de prenda</button><button type="button" className="secondary-button" onClick={()=>{if(!window.confirm('¿Descartar la prenda sin guardar?'))return;try{if(draftKey)localStorage.removeItem(draftKey);setDraft(null)}catch{setMessage('No se pudo descartar el borrador.')}}}>Descartar borrador</button></div>}
    <LocalEntryPanel key={version} title="Nuevo artículo" action="Cargar artículo" endpoint="/api/v1/operations/articles" fields={fields} duplicateNames={duplicateNames} articlePhotos pricePreview initialOpen={initialOpen} externalBusy={busy} onOpenChange={setOpen}
      headerContent={<label className="catalog-article-kind">Tipo de producto<select value={kind} disabled={busy} onChange={(event)=>{setKind(event.target.value);setMessage('')}}><option value="ARTICULO">Artículo común · stock general</option><option value="INDUMENTARIA">Indumentaria · stock por talle</option><option value="CALZADO">Calzado · stock por número</option></select></label>}
      replacementForm={kind==='ARTICULO'?undefined:<NewPurchaseApparelForm key={kind} kind={kind} stockMode draftKey={draftKey} onDraftChange={draftChanged} onBusyChange={setBusy} onSaved={(result)=>{setBusy(false);setKind('ARTICULO');setInitialOpen(false);setOpen(false);setVersion((value)=>value+1);setDraft(null);setMessage(`Prenda guardada con ${Array.isArray(result.items)?result.items.length:0} talles.`);window.dispatchEvent(new Event('alon:refresh'))}} />}
    />
    {message&&<p className="entry-message" role="status">{message}</p>}
  </>
}
