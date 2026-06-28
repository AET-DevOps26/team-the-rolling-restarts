"use client";

import Link from "next/link";
import { useActionState } from "react";

import { AuthShellCard } from "@/components/layout/auth-shell-card";
import { Button } from "@/components/ui/button";
import {
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { register } from "@/lib/actions/auth";
import { ROUTES } from "@/lib/routes";

export default function SignupPage() {
  const [state, formAction, pending] = useActionState(register, undefined);

  return (
    <AuthShellCard>
      <CardHeader>
        <CardTitle>Create your account</CardTitle>
        <CardDescription>Start your personalised feed in under a minute.</CardDescription>
      </CardHeader>
      <form action={formAction} noValidate>
        <CardContent>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="signup-name">Name</FieldLabel>
              <Input id="signup-name" name="name" autoComplete="name" placeholder="Alex Rivera" />
            </Field>
            <Field>
              <FieldLabel htmlFor="signup-username">Username</FieldLabel>
              <Input
                id="signup-username"
                name="username"
                autoComplete="username"
                placeholder="alexr"
                minLength={3}
                required
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="signup-email">Email</FieldLabel>
              <Input
                id="signup-email"
                name="email"
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                required
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="signup-password">Password</FieldLabel>
              <Input
                id="signup-password"
                name="password"
                type="password"
                autoComplete="new-password"
                minLength={8}
                required
              />
            </Field>
            {state?.error && (
              <p className="text-sm text-destructive" role="alert">
                {state.error}
              </p>
            )}
            <p className="text-xs text-muted-foreground">
              By creating an account you agree to the Terms and Privacy Policy.
            </p>
          </FieldGroup>
        </CardContent>
        <CardFooter className="flex flex-col gap-3">
          <Button type="submit" className="w-full" disabled={pending}>
            {pending ? "Creating account…" : "Create account"}
          </Button>
          <p className="text-center text-sm text-muted-foreground">
            Already have an account?{" "}
            <Link href={ROUTES.login} className="text-primary underline-offset-4 hover:underline">
              Log in
            </Link>
          </p>
        </CardFooter>
      </form>
    </AuthShellCard>
  );
}
