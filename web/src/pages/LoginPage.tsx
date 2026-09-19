import { useState, type FormEvent } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { Button, Card, ErrorBanner, Field, InfoBanner, Input, Modal, Spinner } from "../components/ui";

const DEV_AUTH_ENABLED = import.meta.env.VITE_ENABLE_DEV_AUTH === "true";

export function LoginPage() {
  const { state, signIn, signUp, devSignIn, openPasswordReset } = useAuth();
  const [mode, setMode] = useState<"signIn" | "signUp">("signIn");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  if (state.status === "loggedIn") return <Navigate to="/" replace />;

  const loading = state.status === "loggingIn";

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (mode === "signIn") signIn(email, password);
    else signUp(email, password);
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center gap-2">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-violet-600 text-lg font-bold text-white">OS</div>
          <h1 className="text-xl font-semibold text-slate-900">OnSite</h1>
          <p className="text-sm text-slate-500">Gestão de sessões, clientes e faturas</p>
        </div>

        <Card className="p-6">
          <div className="mb-4 flex rounded-lg bg-slate-100 p-1 text-sm font-medium">
            <button
              className={`flex-1 rounded-md py-1.5 ${mode === "signIn" ? "bg-white shadow-sm" : "text-slate-500"}`}
              onClick={() => setMode("signIn")}
            >
              Entrar
            </button>
            <button
              className={`flex-1 rounded-md py-1.5 ${mode === "signUp" ? "bg-white shadow-sm" : "text-slate-500"}`}
              onClick={() => setMode("signUp")}
            >
              Criar conta
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-3">
            <Field label="E-mail">
              <Input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </Field>
            <Field label="Senha">
              <Input
                type="password"
                autoComplete={mode === "signIn" ? "current-password" : "new-password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </Field>

            {state.status === "loggedOut" && state.error && <ErrorBanner message={state.error} />}
            {state.status === "loggedOut" && state.info && <InfoBanner message={state.info} />}

            <Button type="submit" className="w-full" disabled={loading}>
              {loading && <Spinner className="h-4 w-4" />}
              {mode === "signIn" ? "Entrar" : "Criar conta"}
            </Button>
          </form>

          {mode === "signIn" && (
            <button onClick={openPasswordReset} className="mt-3 w-full text-center text-sm text-violet-600 hover:underline">
              Esqueci minha senha
            </button>
          )}

          {DEV_AUTH_ENABLED && (
            <div className="mt-4 border-t border-dashed border-slate-200 pt-4">
              <p className="mb-2 text-xs text-slate-400">Modo de desenvolvimento (backend local)</p>
              <DevLoginForm onSubmit={devSignIn} loading={loading} />
            </div>
          )}
        </Card>
      </div>

      <PasswordResetModal />
    </div>
  );
}

function DevLoginForm({ onSubmit, loading }: { onSubmit: (email: string) => void; loading: boolean }) {
  const [email, setEmail] = useState("demo@example.com");
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(email);
      }}
      className="flex gap-2"
    >
      <Input value={email} onChange={(e) => setEmail(e.target.value)} className="text-xs" />
      <Button type="submit" variant="secondary" disabled={loading}>
        Entrar (dev)
      </Button>
    </form>
  );
}

function PasswordResetModal() {
  const { passwordReset, dismissPasswordReset, sendPasswordResetCode, confirmPasswordReset } = useAuth();
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");

  if (passwordReset.step === "hidden") return null;

  if (passwordReset.step === "email") {
    return (
      <Modal title="Redefinir senha" onClose={dismissPasswordReset}>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            sendPasswordResetCode(email);
          }}
          className="space-y-3"
        >
          <Field label="Seu e-mail">
            <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          </Field>
          {passwordReset.error && <ErrorBanner message={passwordReset.error} />}
          <Button type="submit" className="w-full" disabled={passwordReset.loading}>
            Enviar código
          </Button>
        </form>
      </Modal>
    );
  }

  return (
    <Modal title="Digite o código recebido" onClose={dismissPasswordReset}>
      <p className="mb-3 text-sm text-slate-500">Enviamos um código para {passwordReset.email}.</p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          confirmPasswordReset(code, newPassword);
        }}
        className="space-y-3"
      >
        <Field label="Código">
          <Input value={code} onChange={(e) => setCode(e.target.value)} required autoFocus />
        </Field>
        <Field label="Nova senha">
          <Input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required />
        </Field>
        {passwordReset.error && <ErrorBanner message={passwordReset.error} />}
        <Button type="submit" className="w-full" disabled={passwordReset.loading}>
          Redefinir e entrar
        </Button>
      </form>
    </Modal>
  );
}
