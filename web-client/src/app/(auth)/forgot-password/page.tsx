"use client";

import { Info } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

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
import { ROUTES } from "@/lib/routes";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");

  return (
    <AuthShellCard>
      <CardHeader>
        <CardTitle>Reset your password</CardTitle>
        <CardDescription>
          Enter the email tied to your account and we&apos;ll send a reset link.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <FieldGroup>
          <p className="flex items-start gap-2 rounded-md border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
            <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
            Password reset isn&apos;t available yet — this is a preview of the flow. No email will
            be sent.
          </p>
          <Field>
            <FieldLabel htmlFor="forgot-email">Email</FieldLabel>
            <Input
              id="forgot-email"
              name="email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled
            />
          </Field>
        </FieldGroup>
      </CardContent>
      <CardFooter className="flex flex-col gap-3">
        <Button type="button" className="w-full" disabled>
          Send reset link
        </Button>
        <p className="text-center text-sm text-muted-foreground">
          Remembered it?{" "}
          <Link
            href={ROUTES.login}
            className="text-primary underline-offset-4 hover:underline"
          >
            Back to sign in
          </Link>
        </p>
      </CardFooter>
    </AuthShellCard>
  );
}
