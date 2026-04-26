import { type Annotation, AnnotationItem } from "@/components/annotation-item"
import { Text } from "@/components/text"

/**
 * Section block for a list of annotations on a checkpoint or span.
 * Renders the same uppercase-mono subhead the reference uses with the
 * count appended in parentheses, then either an italic empty state or
 * the items.
 */
export function AnnotationList({
  annotations,
}: {
  annotations: Annotation[]
}) {
  return (
    <section className="flex flex-col gap-2">
      <Text as="h3" variant="eyebrow" tone="fg-4">
        Annotations ({annotations.length})
      </Text>
      {annotations.length === 0 ? (
        <Text variant="bodySm" tone="fg-4" className="italic">
          No annotations at this checkpoint.
        </Text>
      ) : (
        <div className="flex flex-col">
          {annotations.map((a) => (
            <AnnotationItem key={a.id} annotation={a} />
          ))}
        </div>
      )}
    </section>
  )
}
