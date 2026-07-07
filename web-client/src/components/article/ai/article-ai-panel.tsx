"use client";

import { ArticleExplain } from "@/components/article/ai/article-explain";
import { ArticleQa } from "@/components/article/ai/article-qa";
import { ArticleSentiment } from "@/components/article/ai/article-sentiment";
import { ArticleSummary } from "@/components/article/ai/article-summary";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

export function ArticleAiPanel({ articleId }: { articleId: string }) {
  return (
    <section aria-labelledby="article-ai-heading">
      <Card>
        <CardHeader>
          <CardTitle id="article-ai-heading">AI insights</CardTitle>
          <CardDescription>
            Summarize, explain, analyze sentiment, or ask questions about this article.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Tabs defaultValue="summary">
            <TabsList aria-label="AI features">
              <TabsTrigger value="summary">Summary</TabsTrigger>
              <TabsTrigger value="explain">Explain</TabsTrigger>
              <TabsTrigger value="sentiment">Sentiment</TabsTrigger>
              <TabsTrigger value="qa">Q&amp;A</TabsTrigger>
            </TabsList>
            <TabsContent value="summary" className="mt-4">
              <ArticleSummary articleId={articleId} />
            </TabsContent>
            <TabsContent value="explain" className="mt-4">
              <ArticleExplain articleId={articleId} />
            </TabsContent>
            <TabsContent value="sentiment" className="mt-4">
              <ArticleSentiment articleId={articleId} />
            </TabsContent>
            <TabsContent value="qa" className="mt-4">
              <ArticleQa articleId={articleId} />
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </section>
  );
}
