// Every backend call goes through /api. In development the dev server proxies it
// to http://localhost:8080 (see proxy.conf.json), which also avoids CORS.
export const API_BASE = '/api';
