interface Props {
  url: string;
}

export default function SwaggerEmbed({ url }: Props) {
  return (
    <iframe
      src={url}
      title="Swagger UI"
      className="w-full h-full min-h-[600px] border-0 rounded"
      sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
    />
  );
}
