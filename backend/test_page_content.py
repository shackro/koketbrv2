import unittest

from pydantic import ValidationError

from app.impact import DEFAULT_IMPACT, ImpactContent
from app.roadmap import DEFAULT_ROADMAP, RoadmapContent


class PageContentCompatibilityTests(unittest.TestCase):
    def test_impact_fills_new_page_controls_for_existing_content(self):
        payload = DEFAULT_IMPACT.model_dump(mode="json")
        for field in (
            "pathways_eyebrow",
            "pathways_title_first",
            "pathways_title_highlight",
            "pathways_copy",
            "accountability_label",
            "unpublished_report_title",
            "unpublished_report_body",
            "report_link_label",
            "resource_reporting_label",
            "resource_empty_title",
            "resource_empty_body",
            "goals_eyebrow",
            "goals_body",
        ):
            payload.pop(field)
        payload["hero"].pop("image_url")
        for pathway in payload["pathways"]:
            pathway.pop("image_url")

        content = ImpactContent.model_validate(payload)

        self.assertEqual(content.hero.image_url, "")
        self.assertEqual(content.pathways[0].image_url, "")
        self.assertEqual(content.pathways_eyebrow, "A shared effort")
        self.assertEqual(content.resource_reporting_label, "Resource reporting")

    def test_impact_media_accepts_uploaded_paths_and_https(self):
        payload = DEFAULT_IMPACT.model_dump(mode="json")
        payload["hero"]["image_url"] = "/api/media/impact-hero"
        payload["pathways"][0]["image_url"] = "https://cdn.example.test/pathway.webp"

        content = ImpactContent.model_validate(payload)

        self.assertEqual(content.hero.image_url, "/api/media/impact-hero")
        self.assertEqual(content.pathways[0].image_url, "https://cdn.example.test/pathway.webp")

    def test_impact_media_rejects_insecure_external_urls(self):
        payload = DEFAULT_IMPACT.model_dump(mode="json")
        payload["hero"]["image_url"] = "http://example.test/impact.jpg"

        with self.assertRaises(ValidationError):
            ImpactContent.model_validate(payload)

    def test_roadmap_fills_new_page_controls_for_existing_content(self):
        payload = DEFAULT_ROADMAP.model_dump(mode="json")
        for field in (
            "hero_image_url",
            "hero_cta_label",
            "journey_eyebrow",
            "journey_title_first",
            "journey_title_highlight",
            "feedback_eyebrow",
            "feedback_success_message",
            "feedback_button_label",
            "feedback_placeholder",
        ):
            payload.pop(field)
        for phase in payload["phases"]:
            phase.pop("image_url")

        content = RoadmapContent.model_validate(payload)

        self.assertEqual(content.hero_image_url, "")
        self.assertEqual(content.phases[0].image_url, "")
        self.assertEqual(content.journey_eyebrow, "A shared direction")
        self.assertEqual(content.feedback_button_label, "Send feedback")

    def test_roadmap_media_rejects_insecure_external_urls(self):
        payload = DEFAULT_ROADMAP.model_dump(mode="json")
        payload["hero_image_url"] = "http://example.test/roadmap.jpg"

        with self.assertRaises(ValidationError):
            RoadmapContent.model_validate(payload)


if __name__ == "__main__":
    unittest.main()
