import { SupabaseAuthError } from "./supabaseAuth";

/** Portuguese messages for Supabase's error_code, mirroring AuthViewModel.mapAuthError. */
export function mapAuthError(e: unknown, fallback = "Não foi possível entrar. Tente novamente."): string {
  if (!(e instanceof SupabaseAuthError)) return fallback;
  switch (e.code) {
    case "user_already_exists":
      return "Este e-mail já está cadastrado.";
    case "invalid_credentials":
    case "invalid_grant":
      return "E-mail ou senha inválidos.";
    case "email_not_confirmed":
      return "Confirme seu e-mail antes de entrar.";
    case "weak_password":
      return "A senha é muito curta (mínimo 6 caracteres).";
    case "otp_expired":
    case "otp_disabled":
      return "Código inválido ou expirado.";
    case "over_email_send_rate_limit":
      return "Você já pediu um código recentemente. Aguarde um minuto antes de tentar de novo.";
    default:
      return fallback;
  }
}
