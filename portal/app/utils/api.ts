import axios, { type AxiosError } from "axios";
import { useAuthStore } from "../stores/authStore";

const api = axios.create({
  baseURL: "",  // nginx reverse proxy → same-origin
  timeout: 15000,
  headers: { "Content-Type": "application/json" },
});

/* ── Request: 헤더 자동 주입 ──────────────────────────────── */
api.interceptors.request.use((config) => {
  const { token, employeeNumber, clientApp } = useAuthStore.getState();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (employeeNumber) {
    config.headers["Employee-Number"] = employeeNumber;
  }
  if (clientApp) {
    config.headers["Client-App"] = clientApp;
  }

  return config;
});

/* ── Response: 401/403 자동 로그아웃 ──────────────────────── */
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ message?: string; error?: string }>) => {
    const status = error.response?.status;

    if (status === 401 || status === 403) {
      const currentPath = window.location.pathname;
      // 로그인 페이지에서 발생한 인증 오류는 redirect 하지 않음
      if (currentPath !== "/login") {
        useAuthStore.getState().logout();
        window.location.href = "/login";
      }
    }

    // 서버 에러 메시지를 Error.message에 담아 전달
    const serverMessage =
      error.response?.data?.message ??
      error.response?.data?.error ??
      error.message;
    return Promise.reject(new Error(serverMessage));
  },
);

export default api;
