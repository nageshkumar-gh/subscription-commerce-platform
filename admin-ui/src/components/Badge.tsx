export const Badge=({value}:{value:string})=><span className={`badge badge--${value.toLowerCase()}`}>{value.replaceAll('_',' ')}</span>;
