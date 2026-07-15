"use client";

import { useActionState, useEffect, useState } from "react";
import { toast } from "sonner";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { updateProfile } from "@/lib/actions/user";
import type { ActionResult } from "@/lib/actions/content";
import type { UserProfile } from "@/lib/api/types";

export function ProfileSection({ user }: { user: UserProfile }) {
  const [state, formAction, pending] = useActionState(updateProfile, undefined);

  useEffect(() => {
    if (state?.ok) toast.success("Profile saved");
  }, [state]);

  return (
    <section id="profile" className="scroll-mt-20">
      <Card>
        <CardHeader>
          <CardTitle>Profile</CardTitle>
          <CardDescription>How you appear to the rest of NewsLens.</CardDescription>
        </CardHeader>
        <ProfileForm
          key={`${user.name ?? ""}-${user.email ?? ""}`}
          user={user}
          formAction={formAction}
          pending={pending}
          state={state}
        />
      </Card>
    </section>
  );
}

function ProfileForm({
  user,
  formAction,
  pending,
  state,
}: {
  user: UserProfile;
  formAction: (formData: FormData) => void;
  pending: boolean;
  state: ActionResult | undefined;
}) {
  const [name, setName] = useState(user.name ?? "");
  const [email, setEmail] = useState(user.email ?? "");

  return (
    <form action={formAction} aria-busy={pending}>
      <CardContent className="flex flex-col gap-6">
        <div className="flex items-center gap-4">
          <Avatar className="size-16">
            <AvatarFallback>{user.avatarInitials}</AvatarFallback>
          </Avatar>
          <Button type="button" variant="outline" size="sm">
            Change photo
          </Button>
        </div>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="profile-name">Name</FieldLabel>
            <Input
              id="profile-name"
              name="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={pending}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="profile-email">Email</FieldLabel>
            <Input
              id="profile-email"
              name="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={pending}
            />
          </Field>
        </FieldGroup>
        {state && !state.ok && (
          <p className="text-sm text-destructive" role="alert">
            {state.error}
          </p>
        )}
      </CardContent>
      <CardFooter className="justify-end">
        <Button type="submit" size="sm" disabled={pending}>
          {pending ? "Saving…" : "Save profile"}
        </Button>
      </CardFooter>
    </form>
  );
}
